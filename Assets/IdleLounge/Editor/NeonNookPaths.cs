using System;
using System.IO;
public static class NeonNookPaths
{
    public static string OutputDirectory
    {
        get
        {
            string folder = Environment.GetEnvironmentVariable("NEON_NOOK_OUTPUT_DIR");
            if (string.IsNullOrWhiteSpace(folder)) folder = Path.GetFullPath("Builds");
            Directory.CreateDirectory(folder);
            return folder;
        }
    }
}
