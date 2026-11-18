using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.RegularExpressions;

public static class InstallerArguments
{
    // Windows Installer has its own NAME="value" property syntax.
    // This string is passed directly to msiexec, without a command shell.
    public static string Format(IEnumerable<string> arguments) => string.Join(" ", arguments.Select(FormatOne));

    private static string FormatOne(string argument)
    {
        if (argument.IndexOfAny(new[] { '\0', '\r', '\n' }) >= 0)
            throw new ArgumentException("Invalid installer argument.");
        var property = Regex.Match(argument, @"^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
        if (property.Success) return property.Groups[1].Value + "=" + Quote(property.Groups[2].Value);
        return argument.Length == 0 || argument.Any(char.IsWhiteSpace) || argument.Contains('"') ? Quote(argument) : argument;
    }

    private static string Quote(string value) => "\"" + value.Replace("\"", "\"\"") + "\"";
}
