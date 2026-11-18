var root = Path.Combine(Path.GetTempPath(), "mylive-junction-" + Guid.NewGuid().ToString("N"));
var links = new List<string>();
try
{
    Directory.CreateDirectory(root);
    foreach (var name in new[] { "live&record", "直播&录制" })
    {
        var target = Path.Combine(root, name);
        Directory.CreateDirectory(target);
        File.WriteAllText(Path.Combine(target, "marker.txt"), name);
        var link = Path.Combine(root, "link-" + links.Count);
        DirectoryJunction.Create(link, target);
        links.Add(link);
        var read = File.ReadAllText(Path.Combine(link, "marker.txt"));
        if (read != name)
        {
            Console.Error.WriteLine("Junction did not preserve '" + name + "'. Read '" + read + "'.");
            return 1;
        }
    }
    return 0;
}
catch (Exception error)
{
    Console.Error.WriteLine(error.GetType().Name + ": " + error.Message);
    return 1;
}
finally
{
    foreach (var link in links)
    {
        try { Directory.Delete(link); } catch { }
    }
    try { Directory.Delete(root, recursive: true); } catch { }
}
