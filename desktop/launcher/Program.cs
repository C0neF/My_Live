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
            // "could not find java.dll". Prefer an existing 8.3 path, otherwise a junction
            // whose own path is ASCII. The caller's working directory is unchanged.
            var application = AsciiLaunchPath(extracted);
            Console.Error.WriteLine("launching bundled runtime: " + application);
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
        var shortened = ShortPath(extractedExecutable);
        if (shortened != null && File.Exists(shortened)) return shortened;
        var sourceDir = Path.GetDirectoryName(extractedExecutable) ?? throw new IOException("Application runtime is missing.");
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(sourceDir)))[..16];
        var link = Path.Combine(AsciiRoot(), hash);
        var executable = Path.Combine(link, "MyLive.exe");
        using var gate = new Mutex(false, @"Local\MyLiveAscii" + hash);
        try { gate.WaitOne(); }
        catch (AbandonedMutexException) { }
        try
        {
            if (!File.Exists(executable))
            {
                if (Directory.Exists(link)) Directory.Delete(link);
                Directory.CreateDirectory(Path.GetDirectoryName(link)!);
                CreateJunction(link, sourceDir);
            }
        }
        finally { gate.ReleaseMutex(); }
        if (!File.Exists(executable)) throw new IOException("Could not create an ASCII path for the bundled runtime.");
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

    private static string? ShortPath(string path)
    {
        var buffer = new StringBuilder(1024);
        var length = GetShortPathNameW(path, buffer, buffer.Capacity);
        if (length <= 0 || length >= buffer.Capacity) return null;
        var shortened = buffer.ToString();
        if (!shortened.All(static character => character <= 0x7F)) return null;
        if (string.Equals(shortened, path, StringComparison.OrdinalIgnoreCase)) return null;
        return shortened;
    }

    private static void CreateJunction(string link, string target)
    {
        var info = new ProcessStartInfo("cmd.exe")
        {
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardError = true,
            RedirectStandardOutput = true
        };
        info.ArgumentList.Add("/d");
        info.ArgumentList.Add("/c");
        info.ArgumentList.Add("mklink");
        info.ArgumentList.Add("/J");
        info.ArgumentList.Add(link);
        info.ArgumentList.Add(target);
        using var process = Process.Start(info) ?? throw new IOException("Could not create an ASCII path for the bundled runtime.");
        process.WaitForExit();
        if (process.ExitCode != 0)
            throw new IOException("Could not create an ASCII path for the bundled runtime.");
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true, EntryPoint = "GetShortPathNameW")]
    private static extern int GetShortPathNameW(string longPath, StringBuilder shortPath, int bufferSize);

    [DllImport("user32.dll", EntryPoint = "MessageBoxW", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr owner, string message, string title, uint type);
}
