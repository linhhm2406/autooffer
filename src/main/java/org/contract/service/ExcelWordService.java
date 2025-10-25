package org.contract.service;

import org.apache.poi.ss.usermodel.*;
import org.contract.model.RowData;
import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBElement;
import java.io.*;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

@Service
public class ExcelWordService {

    @Value("${file.output-dir}")
    private String outputDir;

    private String formatNumber(String input, char separator) {
        try {
            // Chuyển input sang double (hỗ trợ cả 3.8E7)
            double value = Double.parseDouble(input);

            // Tạo DecimalFormatSymbols với grouping separator theo separator truyền vào
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setGroupingSeparator(separator);
            symbols.setDecimalSeparator('.'); // luôn dùng '.' cho decimal

            DecimalFormat df = new DecimalFormat("#,###", symbols);
            df.setGroupingSize(3);
            df.setGroupingUsed(true);

            return df.format(value);
        } catch (NumberFormatException e) {
            return input; // nếu input không phải số thì trả về nguyên
        }
    }

    /**
     * Đọc Excel, duyệt từng dòng và fill dữ liệu vào template Word qua bookmark
     */
    public List<File> processExcelByBookmark(InputStream excelStream, String templateCode) throws Exception {
        List<RowData> rows = parseExcel(excelStream);
        InputStream templateStream = getTemplate(templateCode);

        if (templateStream == null)
            throw new FileNotFoundException("Không tìm thấy template: " + templateCode);

        new File(outputDir).mkdirs();
        List<File> generatedFiles = new ArrayList<>();

        int index = 1;
        for (RowData row : rows) {
            Map<String, String> data = new HashMap<>(row.getData());

            // 1️⃣ Xử lý key money*
            Map<String, String> snapshot1 = new HashMap<>(data); // bản sao để duyệt
            snapshot1.forEach((k, v) -> {
                if (k.startsWith("money")) {
                    v = v.replace(".", "").replace(",", "");
                    data.put(k, formatNumber(v, '.'));      // 38,000,000
                    data.put(k + "en", formatNumber(v, ','));// 38.000.000
                }
                if (k.startsWith("period")) {
                    data.put(k + "day", String.valueOf(Integer.parseInt(v) * 30)) ;
                }
            });

            // 2️⃣ Tạo các key k1..k9 dựa trên data đã update vòng 1
            Map<String, String> snapshot2 = new HashMap<>(data); // bản sao của data sau vòng 1
            snapshot2.forEach((k, v) -> {
                for (int i = 1; i < 10; i++) {
                    data.put(k + i, v);
                }
            });
            try (InputStream templateCopy = getTemplate(templateCode)) {
                WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(templateCopy);
                fillBookmarks(wordMLPackage, data);

                String fileName = data.getOrDefault("name", "Output" + index);
                File outFile = new File(outputDir, fileName + ".docx");

                wordMLPackage.save(outFile);
                generatedFiles.add(outFile);
                index++;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return generatedFiles;
    }

    /**
     * Đọc file Excel thành danh sách dòng dữ liệu
     */
    private List<RowData> parseExcel(InputStream stream) throws IOException {
        List<RowData> result = new ArrayList<>();
        Workbook workbook = WorkbookFactory.create(stream);
        Sheet sheet = workbook.getSheetAt(0);

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return result;

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) headers.add(cell.getStringCellValue().trim());

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Map<String, String> data = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j);
                data.put(headers.get(j), getCellValue(cell));
            }
            result.add(new RowData(data));
        }

        workbook.close();
        return result;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double val = cell.getNumericCellValue();
                DecimalFormat df = new DecimalFormat("#,###");
                df.setGroupingSize(3);
                df.setGroupingUsed(true);
                String replace = df.format(val).replace(",", "").replace(".", "");
                return replace;
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    private InputStream getTemplate(String templateCode) {
        return getClass().getResourceAsStream("/templates/" + templateCode + ".docx");
    }

    /**
     * Điền text vào bookmark theo map dữ liệu
     */
    private void fillBookmarks(WordprocessingMLPackage wordMLPackage, Map<String, String> values) throws Exception {
        // Lấy tất cả bookmarkStart trong document
        List<Object> bookmarkStarts = wordMLPackage.getMainDocumentPart()
                .getJAXBNodesViaXPath("//w:bookmarkStart", true);

        for (Object obj : bookmarkStarts) {
            CTBookmark bookmark = null;

            if (obj instanceof JAXBElement<?> jaxb && jaxb.getValue() instanceof CTBookmark bm) {
                bookmark = bm;
            } else if (obj instanceof CTBookmark bm) {
                bookmark = bm;
            }

            if (bookmark == null) continue;

            String name = bookmark.getName();
            if (!values.containsKey(name)) continue;

            String textValue = values.get(name);
            BigInteger bookmarkId = bookmark.getId();

            P parentP = (P) XmlUtils.unwrap(bookmark.getParent());
            List<Object> content = parentP.getContent();

            int startIndex = -1;
            int endIndex = -1;

            // 1️⃣ Tìm start & end index trong parent P
            for (int i = 0; i < content.size(); i++) {
                Object o = XmlUtils.unwrap(content.get(i));
                if (o instanceof CTBookmark bm && bm.getId().equals(bookmarkId)) startIndex = i;
                if (o instanceof CTMarkupRange mr && mr.getId().equals(bookmarkId)) {
                    endIndex = i;
                    break;
                }
            }

            if (startIndex == -1 || endIndex == -1) continue;

            // 2️⃣ Xóa tất cả Text trong các run giữa start và end, lấy run đầu tiên làm target
            R targetRun = null;
            for (int i = startIndex + 1; i < endIndex; i++) {
                Object o = XmlUtils.unwrap(content.get(i));

                // Deep unwrap nếu là JAXBElement
                while (o instanceof JAXBElement<?>) {
                    o = ((JAXBElement<?>) o).getValue();
                }

                // Bỏ qua các loại không phải run
                if (o instanceof R r) {
                    // Xóa toàn bộ text cũ trong run để replace
                    r.getContent().removeIf(rc -> {
                        Object unwrapped = XmlUtils.unwrap(rc);
                        return unwrapped instanceof Text;
                    });
                    targetRun = r;
                    break; // dùng run đầu tiên hợp lệ
                }
            }

// 3️⃣ Nếu không tìm được run nào → thử run ngay sau bookmarkStart
            if (targetRun == null && startIndex + 1 < content.size()) {
                Object next = XmlUtils.unwrap(content.get(startIndex + 1));
                while (next instanceof JAXBElement<?>) next = ((JAXBElement<?>) next).getValue();
                if (next instanceof R r) {
                    r.getContent().removeIf(rc -> XmlUtils.unwrap(rc) instanceof Text);
                    targetRun = r;
                }
            }

// 4️⃣ Nếu vẫn không có run → tạo run mới, copy RPr từ run trước bookmark nếu có
            if (targetRun == null) {
                targetRun = new R();
                if (startIndex > 0) {
                    Object prev = XmlUtils.unwrap(content.get(startIndex - 1));
                    while (prev instanceof JAXBElement<?>) prev = ((JAXBElement<?>) prev).getValue();
                    if (prev instanceof R prevRun && prevRun.getRPr() != null) {
                        targetRun.setRPr(XmlUtils.deepCopy(prevRun.getRPr()));
                    }
                }
                content.add(startIndex + 1, targetRun);
            }

// 5️⃣ Thêm text mới vào run, giữ format
            Text text = new Text();
            text.setValue(textValue);
            targetRun.getContent().add(text);
        }
    }





}
