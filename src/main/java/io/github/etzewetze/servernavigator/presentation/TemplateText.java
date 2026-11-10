package io.github.etzewetze.servernavigator.presentation;

/** Accepts TOML literal strings (backslash+n), basic strings and multiline strings alike. */
public final class TemplateText {
    private TemplateText() { }
    public static String normalize(String text) {
        return text.replace("\\n", "\n").replace("\r\n", "\n").replace('\r', '\n');
    }
}
