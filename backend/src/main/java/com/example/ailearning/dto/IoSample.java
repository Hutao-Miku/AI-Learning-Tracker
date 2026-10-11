package com.example.ailearning.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 算法真题的输入 / 输出样例（来自 Codeforces 题面或本地兜底题库）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IoSample {

    private String input;
    private String output;

    public IoSample() {
    }

    public IoSample(String input, String output) {
        this.input = input;
        this.output = output;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
