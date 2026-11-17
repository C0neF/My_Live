using System.Runtime.InteropServices;
using System.Text;

/// <summary>
/// Creates a directory junction without a shell. cmd.exe treats '&amp;' as a command separator,
/// so <c>mklink</c> through <c>cmd /c</c> truncates a target such as <c>D:\直播&amp;录制</c>.
/// </summary>
internal static class DirectoryJunction
{
    public static void Create(string link, string target)
    {
        var fullTarget = Path.GetFullPath(target);
        if (fullTarget.Length == 2 && fullTarget[1] == ':') fullTarget += "\\";
        else fullTarget = fullTarget.TrimEnd('\\');
        if (!Directory.Exists(fullTarget)) throw new DirectoryNotFoundException(fullTarget);

        Directory.CreateDirectory(link);
        var substitute = @"\??\" + fullTarget;
        var substituteBytes = Encoding.Unicode.GetBytes(substitute);
        var printBytes = Encoding.Unicode.GetBytes(fullTarget);
        var pathLength = substituteBytes.Length + 2 + printBytes.Length + 2;
        var reparseDataLength = 8 + pathLength;
        var buffer = new byte[8 + reparseDataLength];
        var cursor = 0;
        Write(buffer, ref cursor, IO_REPARSE_TAG_MOUNT_POINT);
        Write(buffer, ref cursor, (ushort)reparseDataLength);
        Write(buffer, ref cursor, (ushort)0);
        Write(buffer, ref cursor, (ushort)0);
        Write(buffer, ref cursor, (ushort)substituteBytes.Length);
        Write(buffer, ref cursor, (ushort)(substituteBytes.Length + 2));
        Write(buffer, ref cursor, (ushort)printBytes.Length);
        Buffer.BlockCopy(substituteBytes, 0, buffer, cursor, substituteBytes.Length);
        cursor += substituteBytes.Length + 2;
        Buffer.BlockCopy(printBytes, 0, buffer, cursor, printBytes.Length);

        using var handle = CreateFile(
            link,
            GENERIC_WRITE,
            FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
            IntPtr.Zero,
            OPEN_EXISTING,
            FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
            IntPtr.Zero);
        if (handle.IsInvalid) throw new IOException("Could not open the junction directory.");
        if (!DeviceIoControl(handle, FSCTL_SET_REPARSE_POINT, buffer, (uint)buffer.Length, IntPtr.Zero, 0, out _, IntPtr.Zero))
        {
            throw new IOException("Could not create a directory junction for the bundled runtime. Win32 error " + Marshal.GetLastWin32Error());
        }
    }

    private static void Write(byte[] buffer, ref int cursor, uint value)
    {
        buffer[cursor++] = (byte)value;
        buffer[cursor++] = (byte)(value >> 8);
        buffer[cursor++] = (byte)(value >> 16);
        buffer[cursor++] = (byte)(value >> 24);
    }

    private static void Write(byte[] buffer, ref int cursor, ushort value)
    {
        buffer[cursor++] = (byte)value;
        buffer[cursor++] = (byte)(value >> 8);
    }

    private const uint GENERIC_WRITE = 0x40000000;
    private const uint OPEN_EXISTING = 3;
    private const uint FILE_SHARE_READ = 0x00000001;
    private const uint FILE_SHARE_WRITE = 0x00000002;
    private const uint FILE_SHARE_DELETE = 0x00000004;
    private const uint FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;
    private const uint FILE_FLAG_OPEN_REPARSE_POINT = 0x00200000;
    private const uint FSCTL_SET_REPARSE_POINT = 0x000900A4;
    private const uint IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003;

    [System.Runtime.InteropServices.DllImport("kernel32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
    private static extern Microsoft.Win32.SafeHandles.SafeFileHandle CreateFile(
        string lpFileName,
        uint dwDesiredAccess,
        uint dwShareMode,
        IntPtr lpSecurityAttributes,
        uint dwCreationDisposition,
        uint dwFlagsAndAttributes,
        IntPtr hTemplateFile);

    [System.Runtime.InteropServices.DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool DeviceIoControl(
        Microsoft.Win32.SafeHandles.SafeFileHandle hDevice,
        uint dwIoControlCode,
        byte[] lpInBuffer,
        uint nInBufferSize,
        IntPtr lpOutBuffer,
        uint nOutBufferSize,
        out uint lpBytesReturned,
        IntPtr lpOverlapped);
}
