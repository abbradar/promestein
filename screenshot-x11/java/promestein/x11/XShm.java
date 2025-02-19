package promestein.x11;

import jnr.ffi.*;
import jnr.ffi.byref.*;
import jnr.ffi.Runtime;
import jnr.ffi.annotations.Delegate;
import jnr.ffi.util.EnumMapper;

public interface XShm
{
  public static final class XShmSegmentInfo extends Struct
  {
    public final UnsignedLong shmseg = new UnsignedLong(); // ShmSeg
    public final Signed32 shmid = new Signed32();
    public final Pointer shmaddr = new Pointer();
    public final Signed32 readOnly = new Signed32(); // Bool
    
    public XShmSegmentInfo(final Runtime runtime)
    {
      super(runtime);
    }
  }

  boolean XShmQueryExtension(Pointer /* Display */ dpy);
  boolean XShmAttach(Pointer /* Display */ dpy, Pointer /* XShmSegmentInfo */ shminfo);
  boolean XShmDetach(Pointer /* Display */ dpy, Pointer /* XShmSegmentInfo */ shminfo);
  Pointer /* XImage */ XShmCreateImage(Pointer /* Display */ dpy, Pointer /* Visual */ visual,
                                       int depth, X11.ImageFormat format, Pointer data,
                                       Pointer /* XShmSegmentInfo */ shminfo,
                                       int width, int height);
  boolean XShmGetImage(Pointer /* Display */ dpy, NativeLong /* Drawable */ d, Pointer /* XImage */ image,
                       int x, int y, NativeLong plane_mask);
}
