package com.nl2sql.query;

import java.util.List;

/**
 * 反问澄清结果（REQ-512、P1-3）。
 */
public record Clarification(String question, List<String> options) {
}
