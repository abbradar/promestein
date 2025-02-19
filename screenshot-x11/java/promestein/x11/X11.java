package promestein.x11;

import jnr.ffi.*;
import jnr.ffi.byref.*;
import jnr.ffi.Runtime;
import jnr.ffi.annotations.Delegate;
import jnr.ffi.util.EnumMapper;

public interface X11
{
  public static final class XWindowAttributes extends Struct
  {
    public final Signed32 x = new Signed32();
    public final Signed32 y = new Signed32();
    public final Signed32 width = new Signed32();
    public final Signed32 height = new Signed32();
    public final Signed32 border_width = new Signed32();
    public final Signed32 depth = new Signed32();
    public final Pointer visual = new Pointer(); // Visual
    public final UnsignedLong root = new UnsignedLong(); // Window
    public final Signed32 c_class = new Signed32();
    public final Signed32 bit_gravity = new Signed32();
    public final Signed32 win_gravity = new Signed32();
    public final Signed32 backing_sore = new Signed32();
    public final UnsignedLong backing_planes = new UnsignedLong();
    public final UnsignedLong backing_pixel = new UnsignedLong();
    public final Signed32 save_under = new Signed32(); // Bool
    public final UnsignedLong colormap = new UnsignedLong(); // Colormap
    public final Signed32 map_installed = new Signed32(); // Bool
    public final Signed32 map_state = new Signed32();
    public final SignedLong all_event_masks = new SignedLong();
    public final SignedLong your_event_mask = new SignedLong();
    public final SignedLong do_not_propagate_mask = new SignedLong();
    public final Signed32 override_redirect = new Signed32(); // Bool
    public final Pointer screen = new Pointer(); // Screen
    
    public XWindowAttributes(final Runtime runtime)
    {
      super(runtime);
    }
  }

  public static interface CreateImage
  {
    @Delegate public Pointer /* XImage */ call(Pointer /* Display */ display, Pointer /* Visual */ visual,
                                               int depth, int format, int offset,
                                               Pointer /* char* */ data, int width, int height,
                                               int bitmap_pad, int bytes_per_line);
  }

  public static interface DestroyImage
  {
    @Delegate public int call(Pointer /* XImage */ ximage);
  }

  public static interface GetPixel
  {
    @Delegate public NativeLong call(Pointer /* XImage */ ximage, int x, int y);
  }

  public static interface PutPixel
  {
    @Delegate public int call(Pointer /* XImage */ ximage, int x, int y, NativeLong pixel);
  }

  public static interface SubImage
  {
    @Delegate public Pointer /* XImage */ call(Pointer /* XImage */ ximage, int x, int y, int width, int height);
  }

  public static interface AddPixel
  {
    @Delegate public int call(Pointer /* XImage */ ximage, NativeLong value);
  }

  public static final class XImageFuncs extends Struct
  {
    public final Pointer create_image = new Pointer(); // CreateImage
    public final Pointer destroy_image = new Pointer(); // DestroyImage
    public final Pointer get_pixel = new Pointer(); // GetPixel
    public final Pointer put_pixel = new Pointer(); // PutPixel
    public final Pointer sub_image = new Pointer(); // SubImage
    public final Pointer add_pixel = new Pointer(); // AddPixel
    
    public XImageFuncs(final jnr.ffi.Runtime runtime)
    {
      super(runtime);
    }
  }
  
  public static final class XImage extends Struct
  {
    public final Signed32 width = new Signed32();
    public final Signed32 height = new Signed32();
    public final Signed32 xoffset = new Signed32();
    public final Signed32 format = new Signed32();
    public final Pointer data = new Pointer(); // char*
    public final Enum32<ByteOrder> byte_order = new Enum32<ByteOrder>(ByteOrder.class);
    public final Signed32 bitmap_unit = new Signed32();
    public final Signed32 bitmap_bit_order = new Signed32();
    public final Signed32 bitmap_pad = new Signed32();
    public final Signed32 depth = new Signed32();
    public final Signed32 bytes_per_line = new Signed32();
    public final Signed32 bits_per_pixel = new Signed32();
    public final UnsignedLong red_mask = new UnsignedLong();
    public final UnsignedLong green_mask = new UnsignedLong();
    public final UnsignedLong blue_mask = new UnsignedLong();
    public final Pointer obdata = new Pointer(); // XPointer
    public final XImageFuncs f = inner(new XImageFuncs(getRuntime()));
    
    public XImage(final Runtime runtime)
    {
      super(runtime);
    }
  }

  public static enum ImageFormat implements EnumMapper.IntegerEnum
  {
    XYBitmap(0),
    XYPixmap(1),
    ZPixmap(2);

    private final int value;

    ImageFormat(int value) {
      this.value = value;
    }

    public int intValue() {
      return this.value;
    }
  }

  public static enum ByteOrder implements EnumMapper.IntegerEnum
  {
    LSBFirst(0),
    MSBFirst(1);

    private final int value;

    ByteOrder(int value) {
      this.value = value;
    }

    public int intValue() {
      return this.value;
    }
  }

  public static enum Status implements EnumMapper.IntegerEnum
  {
    Success(0),
    BadRequest(1),
    BadValue(2),
    BadWindow(3),
    BadPixmap(4),
    BadAtom(5),
    BadCursor(6),
    BadFont(7),
    BadMatch(8),
    BadDrawable(9),
    BadAccess(10),
    BadAlloc(11),
    BadColor(12),
    BadGC(13),
    BadIDChoice(14),
    BadName(15),
    BadLength(16),
    BadImplementation(17);

    private final int value;

    Status(int value) {
      this.value = value;
    }

    public int intValue() {
      return this.value;
    }
  }

  public static final class XErrorEvent extends Struct
  {
    public final Signed32 type = new Signed32();
    public final Pointer display = new Pointer(); // Display
    public final UnsignedLong resourceid = new UnsignedLong(); // XID
    public final UnsignedLong serial = new UnsignedLong();
    public final Unsigned8 error_code = new Unsigned8();
    public final Unsigned8 request_code = new Unsigned8();
    public final Unsigned8 minor_code = new Unsigned8();
    
    public XErrorEvent(final Runtime runtime)
    {
      super(runtime);
    }
  }

  public static interface XErrorHandler
  {
    @Delegate public int call(Pointer /* Display */ display, Pointer /* XErrorEvent */ event);
  }

  NativeLong /* Atom */ XA_WINDOW = new NativeLong(33L);
  NativeLong /* Window */ PointerRoot = new NativeLong(1L);
  NativeLong AllPlanes = new NativeLong(~0L);

  int XFree(Pointer data); // 1 on success
  XErrorHandler XSetErrorHandler(XErrorHandler handler);
  
  Pointer /* Display */ XOpenDisplay(String display_name);
  int XCloseDisplay(Pointer /* Display */ display); // 0 on success

  int XSync(Pointer /* Display */ display, boolean discard); // 1 on success

  NativeLong /* Window */ XDefaultRootWindow(Pointer /* Display */ display);
  Pointer /* Screen */ XDefaultScreen(Pointer /* Display */ display);
  Pointer /* Visual */ XDefaultVisualOfScreen(Pointer /* Screen */ screen);
  int XDefaultDepthOfScreen(Pointer /* Screen */ screen);

  NativeLong /* Atom */ XInternAtom(Pointer /* Display */ display, String name, boolean only_if_exists);
  String XGetAtomName(Pointer /* Display */ display, NativeLong /* Atom */ atom);

  NativeLong /* Window */ XRootWindow(Pointer /* Display */ display, Pointer /* Screen */ screen);

  Status XGetWindowProperty(Pointer /* Display */ display, NativeLong /* Window */ w,
                            NativeLong /* Atom */ property, NativeLong long_offset,
                            NativeLong long_length, boolean delete,
                            NativeLong /* Atom */ reg_type,
                            NativeLongByReference /* AtomByReference */ actual_type_return,
                            IntByReference actual_format_return,
                            NativeLongByReference nitems_return, NativeLongByReference bytes_after_return,
                            PointerByReference prop_return);
  
  int XGetInputFocus(Pointer /* Display */ display, NativeLongByReference /* WindowByReference */ focus_return,
                     IntByReference revert_to_return); // 1 on success

  int XGetWindowAttributes(Pointer /* Display */ display, NativeLong /* Window */ w,
                           Pointer /* XWindowAttributes */ window_attributes_return); // 1 on success

  Pointer /* XImage */ XGetImage(Pointer /* Display */ display, NativeLong /* Drawable */ d, int x, int y, int width,
                                 int height, NativeLong plane_mask, ImageFormat format);

  int XDestroyImage(Pointer /* XImage */ ximage); // 1 on success
  NativeLong XGetPixel(Pointer /* XImage */ ximage, int x, int y);
}
