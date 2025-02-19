package promestein.posix;

import jnr.ffi.*;
import jnr.ffi.types.size_t;

public interface SHM
{
  int IPC_CREAT = 01000;
  int IPC_PRIVATE = 0;
  int IPC_RMID = 0;
  int SHM_RDONLY = 010000;

  int shmget(int key, @size_t long size, int shmflg);
  int shmctl(int shmid, int cmd, Pointer buf);
  Pointer shmat(int shmid, Pointer shmaddr, int shmflg);
  int shmdt(Pointer shmaddr);
}
