using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;

internal static class Program
{
    [STAThread]
    private static int Main(string[] args)
    {
        try
        {
            // IncludeAllContentForSelfExtract gives the JVM app a stable, versioned cache.
            // Its user library and protected credentials still live in LOCALAPPDATA/MyLive.
            var extracted = Path.Combine(AppContext.BaseDirectory, "MyLive", "MyLive.exe");
            if (!File.Exists(extracted)) throw new FileNotFoundException("Application runtime is missing.");
            // The host names the extraction directory after this executable. A non-ASCII name
            // lands the JVM on a path that the Windows launcher cannot resolve when the ANSI
            // code page does not contain those characters (JDK-8241461): it exits 2 with
            // "could not find java.dll". Launch a same-volume copy from an ASCII directory.
            var application = AsciiLaunchPath(extracted);
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

    private static string AsciiLaunchPath(string extractedExecutable)
    {
        if (extractedExecutable.All(static character => character <= 0x7F)) return extractedExecutable;
        var sourceDir = Path.GetDirectoryName(extractedExecutable) ?? throw new IOException("Application runtime is missing.");
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(sourceDir)))[..16];
        var destination = Path.Combine(AsciiRoot(), hash);
        var executable = Path.Combine(destination, "MyLive.exe");
        using var gate = new Mutex(false, @"Local\MyLiveAscii" + hash);
        try { gate.WaitOne(); }
        catch (AbandonedMutexException) { }
        try
        {
            if (!File.Exists(executable))
            {
                var staging = destination + ".partial";
                if (Directory.Exists(staging)) Directory.Delete(staging, recursive: true);
                CopyDirectory(sourceDir, staging);
                if (Directory.Exists(destination)) Directory.Delete(destination, recursive: true);
                Directory.Move(staging, destination);
            }
        }
        finally { gate.ReleaseMutex(); }
        return executable;
    }

    private static string AsciiRoot()
    {
        var temp = Path.GetTempPath();
        if (temp.All(static character => character <= 0x7F)) return Path.Combine(temp, "MyLive", "ascii-runtime");
        var system = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
        var root = Path.GetPathRoot(system);
        if (string.IsNullOrEmpty(root) || root.Any(static character => character > 0x7F))
            throw new IOException("No ASCII directory is available for the bundled runtime.");
        return Path.Combine(root, "MyLive", "ascii-runtime");
    }

    private static void CopyDirectory(string source, string destination)
    {
        Directory.CreateDirectory(destination);
        foreach (var file in Directory.EnumerateFiles(source))
        {
            var info = new FileInfo(file);
            if ((info.Attributes & FileAttributes.ReparsePoint) != 0) continue;
            info.CopyTo(Path.Combine(destination, info.Name));
        }
        foreach (var directory in Directory.EnumerateDirectories(source))
        {
            var info = new DirectoryInfo(directory);
            if ((info.Attributes & FileAttributes.ReparsePoint) != 0) continue;
            CopyDirectory(directory, Path.Combine(destination, info.Name));
        }
    }

    [DllImport("user32.dll", EntryPoint = "MessageBoxW", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr owner, string message, string title, uint type);
}
