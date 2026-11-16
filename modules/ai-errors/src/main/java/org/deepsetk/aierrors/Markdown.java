package org.deepsetk.aierrors;

/**
 * Conversor Markdown -> HTML muy pequeno, pensado para el subconjunto que
 * devuelve Gemini (titulos, listas, negritas, codigo en linea y bloques).
 */
final class Markdown {

    private Markdown() {
    }

    static String toHtml(String markdown) {
        StringBuilder out = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean inCode = false;
        boolean inList = false;

        for (String rawLine : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String line = rawLine.stripTrailing();
            String trimmed = line.strip();

            if (trimmed.startsWith("```")) {
                if (inCode) {
                    out.append("<pre><font face=\"Consolas\" color=\"#c9d0d9\">")
                            .append(code).append("</font></pre>");
                    code.setLength(0);
                    inCode = false;
                } else {
                    flush(out, paragraph);
                    closeList(out, inList);
                    inList = false;
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                code.append(escape(line)).append('\n');
                continue;
            }
            if (trimmed.isEmpty()) {
                flush(out, paragraph);
                closeList(out, inList);
                inList = false;
                continue;
            }
            if (trimmed.startsWith("#")) {
                flush(out, paragraph);
                closeList(out, inList);
                inList = false;
                String text = trimmed.replaceFirst("^#+\\s*", "");
                out.append("<p><b><font size=\"4\">").append(inline(text)).append("</font></b></p>");
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                flush(out, paragraph);
                if (!inList) {
                    out.append("<ul>");
                    inList = true;
                }
                out.append("<li>").append(inline(trimmed.substring(2))).append("</li>");
                continue;
            }
            if (trimmed.matches("^\\d+[.)]\\s+.*")) {
                flush(out, paragraph);
                if (!inList) {
                    out.append("<ul>");
                    inList = true;
                }
                out.append("<li>").append(inline(trimmed.replaceFirst("^\\d+[.)]\\s+", ""))).append("</li>");
                continue;
            }
            closeList(out, inList);
            inList = false;
            if (paragraph.length() > 0) {
                paragraph.append("<br>");
            }
            paragraph.append(inline(trimmed));
        }
        if (inCode) {
            out.append("<pre><font face=\"Consolas\" color=\"#c9d0d9\">").append(code).append("</font></pre>");
        }
        flush(out, paragraph);
        closeList(out, inList);
        return out.toString();
    }

    private static void flush(StringBuilder out, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            out.append("<p>").append(paragraph).append("</p>");
            paragraph.setLength(0);
        }
    }

    private static void closeList(StringBuilder out, boolean inList) {
        if (inList) {
            out.append("</ul>");
        }
    }

    private static String inline(String text) {
        String html = escape(text);
        html = html.replaceAll("`([^`]+)`",
                "<font face=\"Consolas\" color=\"#c99f7a\">$1</font>");
        html = html.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("(?<!\\*)\\*([^*]+)\\*(?!\\*)", "<i>$1</i>");
        return html;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
