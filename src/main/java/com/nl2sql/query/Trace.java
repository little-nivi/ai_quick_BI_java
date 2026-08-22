package com.nl2sql.query;

import java.util.List;

/**
 * 数据溯源信息（REQ-514、P1-2）。
 */
public record Trace(List<String> tables, List<String> fields, String metric,
                    Double confidence, String confidenceLevel) {
}
