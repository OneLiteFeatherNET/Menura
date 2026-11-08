package net.onelitefeather.menura.core.asset;

import net.onelitefeather.menura.core.problem.ProblemException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The declared checksum of an upload, in the form S3 reports for a multipart upload with SHA-256 part checksums:
 * the base64 SHA-256 of the concatenated part digests, followed by a dash and the number of parts. The server cannot
 * hash the bytes itself (principle 1), so the storage's own composite value is what the declaration is compared with.
 */
public record Checksum(String value, int partCount) {

    private static final Pattern FORM = Pattern.compile("([A-Za-z0-9+/]{43}=)-([1-9][0-9]{0,4})");

    public static Checksum parse(String value) {
        Matcher matcher = value == null ? null : FORM.matcher(value);
        if (matcher == null || !matcher.matches()) {
            throw new ProblemException(400, "Invalid asset",
                    "The field 'checksum' must be the SHA-256 of the part digests with the part count, as in 'base64=-3'.");
        }
        return new Checksum(value, Integer.parseInt(matcher.group(2)));
    }
}
