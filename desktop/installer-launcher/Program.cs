using System.Diagnostics;
using System.Runtime.InteropServices;

internal static class Program
{
    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr owner, string text, string caption, uint type);

    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            var msi = Path.Combine(AppContext.BaseDirectory, "payload", "MyLive.msi");
            if (!File.Exists(msi)) throw new FileNotFoundException("The installer payload is missing.");
            var start = new ProcessStartInfo(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "msiexec.exe"))
            {
                UseShellExecute = false,
                CreateNoWindow = true
            };
            var uninstall = args.Any(arg => string.Equals(arg, "uninstall", StringComparison.OrdinalIgnoreCase));
            var forwarded = new List<string> { uninstall ? "/x" : "/i", msi };
            forwarded.AddRange(args.Where(arg => !string.Equals(arg, "uninstall", StringComparison.OrdinalIgnoreCase)));
            start.Arguments = InstallerArguments.Format(forwarded);
            using var process = Process.Start(start) ?? throw new InvalidOperationException("Unable to start Windows Installer.");
            process.WaitForExit();
            return process.ExitCode;
        }
        catch
        {
            if (!args.Any(arg => arg.StartsWith("/q", StringComparison.OrdinalIgnoreCase)))
                MessageBoxW(IntPtr.Zero, "无法启动 Windows Installer。请重新下载，或使用同版本的 MSI 安装包。", "MyLive", 0x10);
            return 1;
        }
    }
}
