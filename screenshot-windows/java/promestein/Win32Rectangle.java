package promestein.windows;

import jnr.ffi.Struct;
import jnr.ffi.Runtime;

public final class Win32Rectangle extends Struct
{
	public final SignedLong left = new SignedLong();
	public final SignedLong top = new SignedLong();
	public final SignedLong right = new SignedLong();
	public final SignedLong bottom = new SignedLong();
	
	public Win32Rectangle(final Runtime runtime)
  {
		super(runtime);
	}
}
