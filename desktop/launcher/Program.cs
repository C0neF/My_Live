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
            // The Windows JVM launcher converts paths with the ANSI code page (JDK-8241461).
            // A non-ASCII executable name, working directory, or argument becomes "?" and the
            // process exits 2 with "could not find java.dll", or NIO rejects the path.
            // Hand the JVM only ASCII paths. The caller's files stay where they are.
            var originalWork = Environment.CurrentDirectory;
            var application = AsciiLaunchPath(extracted);
            var childWork = AsciiDirectory(originalWork);
            Console.Error.WriteLine("launching bundled runtime: " + application);
            var start = new ProcessStartInfo(application)
            {
                UseShellExecute = false,
                WorkingDirectory = childWork
            };
            // Let .NET quote each argument. Never route room URLs or file paths through a shell.
            foreach (var argument in args) start.ArgumentList.Add(AsciiArgument(argument, originalWork));
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

    private static string AsciiArgument(string argument, string originalWork)
    {
        var separator = argument.IndexOf('=');
        if (separator <= 0 || IsAscii(argument)) return argument;
        var name = argument[..separator];
        var value = argument[(separator + 1)..];
        if (name is "--self-check" or "--window-smoke")
            return name + "=" + AsciiDirectory(ResolvePath(value, originalWork), create: true);
        if (name == "--self-check-media")
            return name + "=" + AsciiMedia(value, originalWork);
        return argument;
    }

    private static string AsciiMedia(string value, string originalWork)
    {
        if (IsAscii(value)) return value;
        var full = ResolvePath(value, originalWork);
        if (!File.Exists(full)) return value;
        var asciiName = "mylive-self-check-media" + Path.GetExtension(full);
        var link = Path.Combine(originalWork, asciiName);
        if (!File.Exists(link) && !CreateHardLinkW(link, full, IntPtr.Zero))
            File.Copy(full, link);
        // The child working directory is an ASCII junction of originalWork, so this relative name resolves.
        return asciiName;
    }

    private static string ResolvePath(string value, string originalWork) =>
        Path.IsPathRooted(value) ? value : Path.GetFullPath(Path.Combine(originalWork, value));

    private static string AsciiLaunchPath(string extractedExecutable)
    {
        if (IsAscii(extractedExecutable)) return extractedExecutable;
        var shortened = ShortPath(extractedExecutable);
        if (shortened != null && File.Exists(shortened)) return shortened;
        var sourceDir = Path.GetDirectoryName(extractedExecutable) ?? throw new IOException("Application runtime is missing.");
        return Path.Combine(AsciiDirectory(sourceDir), "MyLive.exe");
    }

    private static string AsciiDirectory(string directory, bool create = false)
    {
        if (IsAscii(directory)) return directory;
        if (create) Directory.CreateDirectory(directory);
        var shortened = ShortPath(directory);
        if (shortened != null && Directory.Exists(shortened)) return shortened;
        var hash = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(directory)))[..16];
        var link = Path.Combine(AsciiRoot(), hash);
        using var gate = new Mutex(false, @"Local\MyLiveAscii" + hash);
        try { gate.WaitOne(); }
        catch (AbandonedMutexException) { }
        try
        {
            if (!Directory.Exists(link))
            {
                Directory.CreateDirectory(Path.GetDirectoryName(link)!);
                CreateJunction(link, directory);
            }
        }
        finally { gate.ReleaseMutex(); }
        return link;
    }

    private static string AsciiRoot()
    {
        var temp = Path.GetTempPath();
        if (IsAscii(temp)) return Path.Combine(temp, "MyLive", "ascii-runtime");
        var system = Environment.GetFolderPath(Environment.SpecialFolder.Windows);
        var root = Path.GetPathRoot(system);
        if (string.IsNullOrEmpty(root) || !IsAscii(root))
            throw new IOException("No ASCII directory is available for the bundled runtime.");
        return Path.Combine(root, "MyLive", "ascii-runtime");
    }

    private static bool IsAscii(string value) => value.All(static character => character <= 0x7F);

    private static string? ShortPath(string path)
    {
        var buffer = new StringBuilder(1024);
        var length = GetShortPathNameW(path, buffer, buffer.Capacity);
        if (length <= 0 || length >= buffer.Capacity) return null;
        var shortened = buffer.ToString();
        if (!IsAscii(shortened) || string.Equals(shortened, path, StringComparison.OrdinalIgnoreCase)) return null;
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

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool CreateHardLinkW(string fileName, string existingFileName, IntPtr security);

    [DllImport("user32.dll", EntryPoint = "MessageBoxW", CharSet = CharSet.Unicode)]
    private static extern int MessageBoxW(IntPtr owner, string message, string title, uint type);
}
