package com.zhongan.devpilot.treesitter;

import com.zhongan.devpilot.util.LanguageUtil;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.treesitter.TSLanguage;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterGo;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterPython;

public class TreeSitterParser {

    private final TSLanguage language;

    private final static Map<String, TreeSitterParser> parserMap = new ConcurrentHashMap<>();

    static {
        parserMap.put("default", new TreeSitterParser(null));
        parserMap.put("java", new TreeSitterParser(new TreeSitterJava()));
        parserMap.put("go", new TreeSitterParser(new TreeSitterGo()));
        parserMap.put("python", new TreeSitterParser(new TreeSitterPython()));
    }

    public TreeSitterParser(TSLanguage language) {
        this.language = language;
    }

    public String clearRedundantWhitespace(String originCode, int position, String output) {
        if (language == null) {
            return output;
        }

        var result = new StringBuilder(output);
        while (result.length() != 0 && result.charAt(0) == ' ') {
            result.deleteCharAt(0);
            if (containsError(buildLineCode(originCode, position, result.toString()))) {
                return " " + result;
            }
        }

        return result.toString();
    }

    public String parse(String originCode, int position, String output) {
        if (!output.startsWith(" ")) {
            return parseInner(originCode, position, output);
        }

        // handle special case : start with several whitespace
        var noWhitespaceResult = parseInner(originCode, position, output.trim());
        var whitespaceResult = parseInner(originCode, position, " " + output.trim());

        var result = whitespaceResult.length() < noWhitespaceResult.length()
                ? noWhitespaceResult : whitespaceResult;

        return clearRedundantWhitespace(originCode, position, result);
    }

    private String parseInner(String originCode, int position, String output) {
        if (language == null) {
            return output;
        }
        var result = new StringBuilder(output);
        while (result.length() != 0) {
            String lineCode = buildLineCode(originCode, position, result.toString());
            var treeString = getTree(lineCode).getRootNode().toString();
            if (StringUtils.contains(treeString, "ERROR")) {
                result.deleteCharAt(result.length() - 1);
            } else if (StringUtils.contains(treeString, "MISSING")) {
                String missing = findMissing(treeString);
                if (StringUtils.isNotBlank(missing)) {
                    result.append(missing);
                    if (containsError(buildLineCode(originCode, position, result.toString()))) {
                        result.deleteCharAt(result.length() - 1);
                    }
                    return result.toString();
                } else {
                    break;
                }
            } else {
                return result.toString();
            }
        }

        return output;
    }

    private String buildLineCode(String originCode, int position, String output) {
        return new StringBuilder(originCode).insert(position, output).toString();
    }

    private boolean containsError(String input) {
        var treeString = getTree(input).getRootNode().toString();
        return treeString.contains("ERROR") || treeString.contains("MISSING");
    }

    private TSTree getTree(String input) {
        var parser = new TSParser();
        parser.setLanguage(language);
        return parser.parseString(null, input);
    }

    private String findMissing(String input) {
        Pattern pattern = Pattern.compile("MISSING\\s+\"([^\"]*?)\"");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public static TreeSitterParser getInstance(String extension) {
        var language = LanguageUtil.getLanguageByExtension(extension);

        if (language == null) {
            return getDefaultParser();
        }

        var parser = parserMap.get(language.getLanguageName().toLowerCase(Locale.ROOT));

        if (parser == null) {
            return getDefaultParser();
        }

        return parser;
    }

    private static TreeSitterParser getDefaultParser() {
        return parserMap.get("default");
    }

}
