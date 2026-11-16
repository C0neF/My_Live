using System.Diagnostics;
using System.Runtime.InteropServices;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            // IncludeAllContentForSelfExtract gives the JVM app a stable, versioned cache.
            // Its user library and protected credentials still live in LOCALAPPDATA/MyLive.
            var application = Path.Combine(AppContext.BaseDirectory, "MyLive", "MyLive.exe");
            if (!File.Exists(application)) throw new FileNotFoundException("Application runtime is missing.");
            var start = new ProcessStartInfo(application)
            {
                UseShellExecute = false,
                WorkingDirectory = Environment.CurrentDirectory
            };
            // Let .NET quote each argument. Never route room URLs or file paths through a shell.
            foreach (var argument in args) start.ArgumentList.Add(argument);
            using var child = Process.Start(start) ?? throw new IOException("Could not start MyLive.");
            child.WaitForExit();
            return child.ExitCode;
        }
        catch (Exception error)
        {
            // Do not echo launch arguments: they can contain URLs or private paths.
            var message = "MyLive 启动失败，请重新打开单文件版；若仍失败，请重新下载程序。\n错误类型：" + error.GetType().Name;
            Console.Error.WriteLine(message);
            if (!args.Any(value => value.StartsWith("--self-check=", StringComparison.Ordinal) || value.StartsWith("--window-smoke=", StringComparison.Ordinal)))
                MessageBoxW(IntPtr.Zero, message, "MyLive", 0x10);
            return 1;
        }
    }

    [DllImport("user32.dll", EntryPoint = "MessageBoxW", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr owner, string message, string title, uint type);
}
