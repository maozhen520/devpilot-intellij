package com.zhongan.codeai.enums;

import java.util.Objects;

public enum EditorActionEnum {
    PERFORMANCE_CHECK("codeai.action.performance.check", "Performance check in the following code",
        "{{selectedCode}}\nGiving the code above, please fix any performance issues." +
            "\nRemember you are very familiar with performance optimization.\n"),
    GENERATE_COMMENTS("codeai.action.generate.comments", "Generate comments in the following code",
        "{{selectedCode}}\nGiving the code above, please generate code comments, return code with comments."),
    GENERATE_TESTS("codeai.action.generate.tests", "Generate Tests in the following code",
        "{{selectedCode}}\nGiving the code above, " +
            "please help to generate JUnit test cases for it, be aware that if the code is untestable, " +
            "please state it and give suggestions instead:"),
    GENERATE_DOCS("codeai.action.generate.docs", "Generate docs in the following code",
        "{{selectedCode}}\nGiving the code above, please generate Javadoc for it."),
    FIX_THIS("codeai.action.fix", "Fix This in the following code",
        "{{selectedCode}}\nGiving the code above, please help to refactor it:\n\n" +
            "- Fix any typos or grammar issues.\n" +
            "- Use better names as replacement to magic numbers or abitrary acronyms.\n" +
            "- Simplify the code so that it's more strait forward and easy to understand.\n" +
            "- Optimize it for performance reasons.\n" +
            "- Refactor it using best practice in software engineering.\n\n" +
            "Please first give detailed explainations about you find outs, then the refactored code."),


    REVIEW_CODE("codeai.action.review", "Review code in the following code",
        "{{selectedCode}}\nGiving the code above, please review the code line by line:\n\n" +
            "- Think carefully, you should be extremely careful.\n" +
            "- Find out if any bugs exists.\n" +
            "- Reveal any bad smell in the code.\n" +
            "- Give optimization or best practice suggestion."),
    EXPLAIN_THIS("codeai.action.explain", "Explain this in the following code",
        "{{selectedCode}}\nGiving the code above, please explain it in detail, line by line");

    private final String label;

    private final String userMessage;

    private final String prompt;

    EditorActionEnum(String label, String userMessage, String prompt) {
        this.label = label;
        this.userMessage = userMessage;
        this.prompt = prompt;
    }

    public static EditorActionEnum getEnumByLabel(String label) {
        if (Objects.isNull(label)) {
            return null;
        }
        for (EditorActionEnum type : EditorActionEnum.values()) {
            if (type.getLabel().equals(label)) {
                return type;
            }
        }
        return null;
    }

    public String getLabel() {
        return label;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
