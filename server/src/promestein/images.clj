(ns promestein.images
  (:import [java.util.concurrent TimeUnit])
  (:import [javax.imageio ImageIO])
  (:import [java.io ByteArrayInputStream])
  (:import [com.google.protobuf ByteString])
  (:import [com.google.auth.oauth2 ServiceAccountCredentials])
  (:import [com.google.api.gax.core BackgroundResource FixedCredentialsProvider])
  (:import [com.google.cloud.vision.v1
            ImageAnnotatorSettings ImageAnnotatorClient Image ImageContext
            AnnotateImageRequest Feature Feature$Type AnnotateImageResponse
            BoundingPoly Vertex Page Block Paragraph Word Symbol])
  (:require [clojure.string :as str])
  (:require [clojure.java.io :as io])
  (:require [clojure.java.jdbc :as db])
  (:require [clojure.tools.trace :refer [trace]])
  (:require [cheshire.core :as json])
  (:require [digest :refer [digest]])
  (:require [honeysql.core :as sql])
  (:require [honeysql.helpers :as h])
  (:require [clojure.tools.logging :as log])

  (:require [promestein.lib :refer :all])
  (:require [promestein.utils :refer :all])
  (:require [promestein.text :as text]))

;;; Shared resources

(defn create-image-processor
  "Create image processor shared resources. Text processor is not considered owned."
  [& {:keys [google-credentials-path text-processor]}]
  (let [credentials (with-open [creds (io/input-stream google-credentials-path)]
                      (ServiceAccountCredentials/fromStream creds))
        credentials-provider (FixedCredentialsProvider/create credentials)
        vision-settings (->
                         (ImageAnnotatorSettings/newBuilder)
                         (.setCredentialsProvider credentials-provider)
                         (.build))]
    {:vision (ImageAnnotatorClient/create ^ImageAnnotatorSettings vision-settings)
     :text-processor text-processor}))

(defn shutdown-background-resource
  "Shuts down BackgroundResource."
  [^BackgroundResource resource]
  (.shutdown resource)
  (.awaitTermination resource 60 (TimeUnit/SECONDS)) ; we don't check for success here
  )

(defn close-image-processor
  "Close image processor resources."
  [{:keys [vision]}]
  (shutdown-background-resource vision))

;;; Image processing

(defn- get-bounding-poly
  "Convert BoundingPoly to a list of points"
  [^BoundingPoly points]
  (map
   (fn [^Vertex p] {:x (.getX p)
                    :y (.getY p)})
   (.getVerticesList points)))

(defn detect-text-on-images
  "Given images and Vision client, run them through Google Vision OCR"
  [^ImageAnnotatorClient vision requests & {:keys [document?] :or {document? false}}]
  ;; Read Google Vision documentation to understand what happens here.
  (->>
   requests
   (map
    (fn [{:keys [^bytes image language]}]
      (let [bytes (ByteString/copyFrom image)
            image (-> (Image/newBuilder)
                      (.setContent bytes)
                      .build)
            feature (-> (Feature/newBuilder)
                        (.setType (if document? (Feature$Type/DOCUMENT_TEXT_DETECTION) (Feature$Type/TEXT_DETECTION)))
                        .build)
            context (let [builder (ImageContext/newBuilder)]
                      (when (some? language) (.addLanguageHints builder language))
                      (.build builder))]
        (-> (AnnotateImageRequest/newBuilder)
            (.addFeatures feature)
            (.setImage image)
            (.setImageContext context)
            .build))))
   (.batchAnnotateImages vision)
   .getResponsesList
   (map
    (fn [^AnnotateImageResponse response]
      (when (.hasError response)
        (let [error (.getError response)]
          (throw (ex-info "Google Vision OCR error"
                          {:message (.getMessage error)
                           :code (.getCode error)}))))
      (->>
       response
       .getFullTextAnnotation
       .getPagesList
       (map
        (fn [^Page page]
          {:wigth (.getWidth page)
           :height (.getHeight page)
           :blocks (map
                    (fn [^Block block]
                      {:confidence (.getConfidence block)
                       :position (-> block .getBoundingBox get-bounding-poly)
                       :paragraphs (map
                                    (fn [^Paragraph para]
                                      {:confidence (.getConfidence para)
                                       :position (-> para .getBoundingBox get-bounding-poly)
                                       :words (map
                                               (fn [^Word word]
                                                 {:confidence (.getConfidence word)
                                                  :position (-> word .getBoundingBox get-bounding-poly)
                                                  :symbols (map
                                                            (fn [^Symbol sym]
                                                              {:confidence (.getConfidence sym)
                                                               :position (-> sym .getBoundingBox get-bounding-poly)
                                                               :text (.getText sym)})
                                                            (.getSymbolsList word))})
                                               (.getWordsList para))})
                                    (.getParagraphsList block))})
                    (.getBlocksList page))})))))))

(def format-mime-types
  {"JPEG" "image/jpeg"
   "PNG" "image/png"})

(defn get-mime-type
  "Get MIME type from image bytes"
  [image-bytes]
  (->> image-bytes
       ByteArrayInputStream.
       ImageIO/createImageInputStream
       ImageIO/getImageReaders
       iterator-seq
       first
       .getFormatName
       str/upper-case
       (get format-mime-types)))

;; State database

(defn create-images-db
  "Create images state database."
  ([db-spec]
   (create-images-db db-spec {}))
  ([db-spec create-options]
   (db/with-db-transaction [conn db-spec]
     (let [my-options (merge create-options sqlite-options)]
       (db/execute!
        conn (db/create-table-ddl
              :images
              [[:id        "integer primary key autoincrement"]
               [:text-hash "text not null unique"]
               [:mime      "text not null"]
               [:data      "blob not null"]]
              my-options))
       (db/execute!
        conn (db/create-table-ddl
              :image-chunks
              [[:image-id   "integer not null references images(id)"]
               [:chunk-id   "integer not null references chunks(id)"]
               [:page-index "integer not null"]
               [:length     "integer not null"]
               [:confidence "float not null"]
               [:position   "text not null"]]
              my-options))
       (db/execute! conn "CREATE INDEX IF NOT EXISTS image_chunks_images ON image_chunks (image_id)")))))

(def image-text-hash-algo "sha-256")

;;; Fetching

(defn get-image-by-text-hash
  "Find image id by the text hash."
  [user-conn text-hash]
  (let [query (sql/format {:select [:id]
                           :from [:images]
                           :where [:= :text-hash text-hash]})]
    (->> (db/query user-conn query sqlite-options)
         first
         :id)))

(defn get-image-bytes
  "Gets image bytes by id"
  [user-conn image-id]
  (let [query (sql/format {:select [:mime :data]
                           :from [:images]
                           :where [:= :id image-id]})]
    (->> (db/query user-conn query sqlite-options)
         first)))

(defn get-image-info
  "Gets image metadata by id"
  [user-conn image-id]
  (let [chunks-query (sql/format {:select [:chunk-id :page-index :length :confidence :position]
                                  :from [:image-chunks]
                                  :where [:= :image-id image-id]})
        chunks (db/query user-conn chunks-query sqlite-options)]
    {:chunks (map (fn [chunk] (update chunk :position #(json/decode % true))) chunks)}))

(defn get-image-stats
  "Gets image stats by id"
  [user-conn image-id]
  (let [query
        (->>
         (-> (h/select [:%count.* :new])
             (h/from [:tokens :t] [:image-chunks :ic])
             (h/where
              [:and
               [:= :ic.image-id image-id]
               [:= :t.chunk-id :ic.chunk-id]
               [:or
                [:= :t.dict-entry-id nil]
                [:= :t.sense-index nil]]]))
         (sql/format))]

    (->> (db/query user-conn query sqlite-options)
         first)))

;;; Processing

(defn process-images
  "Process images (bytes), possibly loading them into user database. Returns image ids."
  [{:keys [vision text-processor] :as processor} user-conn images]
  ;; We run all images through OCR and use their contents hash to determine if they're unique.
  (let [processed (->> (map #(hash-map :image % :language "ja") images)
                       (detect-text-on-images vision)
                       (map (fn [result]
                              ;; DOCUMENT_TEXT_RECOGNITION returns a matryoshka of pages->blocks->paragraphs->words->symbols.
                              ;; We convert each page into a list of blocks with simple text inside.
                              ;; Example result:
                              ;;
                              ;; {:confidence 0.98, :position ({:x 252, :y 6} {:x 408, :y 6} {:x 408, :y 24} {:x 252, :y 24}), :text "RPG"}
                              (let [pages (->>
                                           result
                                           (map-indexed (fn [page-index {:keys [blocks]}]
                                                          (->>
                                                           blocks
                                                           (mapcat :paragraphs)
                                                           (map (fn [{:keys [confidence position words]}]
                                                                  (let [result
                                                                        {:confidence confidence
                                                                         :position position
                                                                         :page-index page-index
                                                                         :text (->>
                                                                                words
                                                                                (map (fn [word]
                                                                                       (->> word
                                                                                            :symbols
                                                                                            (map :text)
                                                                                            (apply str))))
                                                                                (str/join ""))}]
                                                                    (if (-> result :position empty? not)
                                                                      result
                                                                      (log/warn "Ignoring text within empty region:" (-> result :text))))))
                                                           (remove nil?)))))
                                    text-hash (->> pages
                                                   (mapcat (fn [page]
                                                             (->>
                                                              page
                                                              (map :text)
                                                              sort)))
                                                   (str/join "")
                                                   (digest image-text-hash-algo))]
                                {:blocks (apply concat pages)
                                 :text-hash text-hash}))))
        unique-images (->> (map-indexed-n (fn [index image {:keys [text-hash blocks]}]
                                            {text-hash {:image image :blocks blocks :indices [index]}})
                                          images processed)
                           (apply merge-with (fn [{:keys [blocks image indices-1]} {:keys [indices-2]}]
                                               {:blocks blocks :image image :indices (concat indices-1 indices-2)}))
                           (map (fn [[text-hash entry]] (assoc entry :text-hash text-hash))))
        existing-image-ids (map #(->> % :text-hash (get-image-by-text-hash user-conn)) unique-images)
        new-images (keep-n (fn [image-id {:keys [indices image blocks text-hash]}]
                             (when (nil? image-id)
                               {:indices indices
                                :blocks blocks
                                :entry {:data image
                                        :text-hash text-hash
                                        :mime (get-mime-type image)}}))
                           existing-image-ids unique-images)
        new-image-ids (map (comp first vals) (db/insert-multi! user-conn :images (map :entry new-images) sqlite-options))
        new-images-map (apply hash-map (mapcat (fn [{:keys [indices]} image-id] [indices image-id])
                                               new-images new-image-ids))
        new-image-chunks (mapcat (fn [image-id {:keys [blocks]}]
                                   (map #(assoc % :image-id image-id) blocks))
                                 new-image-ids new-images)
        new-chunk-ids (text/process-chunks text-processor user-conn (map :text new-image-chunks))
        new-image-chunk-entries (map (fn [{:keys [image-id confidence position page-index text]} chunk-id]
                                       {:image-id image-id
                                        :chunk-id chunk-id
                                        :page-index page-index
                                        :length (count text)
                                        :confidence confidence
                                        :position (json/encode position)})
                                     new-image-chunks new-chunk-ids)]
    (db/insert-multi! user-conn :image-chunks new-image-chunk-entries sqlite-options)
    (let [combined-images (->> (mapcat (fn [{:keys [indices]} image-id]
                                         (let [id (if (nil? image-id)
                                                    (get new-images-map indices)
                                                    image-id)]
                                           (map #(vector % id) indices)))
                                       unique-images existing-image-ids)
                               (into {}))]
      (map-indexed (fn [index image] (get combined-images index)) images))))
