package org.contract.model;

import java.util.Map;

public class RowData {
    private Map<String, String> data;

    public RowData(Map<String, String> data) {
        this.data = data;
    }

    public Map<String, String> getData() {
        return data;
    }
}
