package com.example.internetspeedmeeter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.graphics.pdf.PdfDocument;
import android.graphics.Paint;
import android.graphics.Canvas;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Exports speed test history as CSV or PDF and fires Android share sheet.
 */
public class ReportExporter {

    private static final String AUTHORITY = "com.example.internetspeedmeeter.fileprovider";

    /** Exports last N speed test results to a CSV file and opens share sheet. */
    public static void exportCsv(Context ctx, List<SpeedTestResult> results) {
        try {
            File dir = new File(ctx.getCacheDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "speed_history_" + timestamp() + ".csv");

            try (PrintWriter pw = new PrintWriter(new FileOutputStream(file))) {
                pw.println("Date,Avg Speed (MB/s),Duration (s)");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                for (SpeedTestResult r : results) {
                    pw.printf("\"%s\",%.2f,%d%n",
                            sdf.format(new Date(r.timestamp)), r.speedMbps, r.durationSec);
                }
            }

            share(ctx, file, "text/csv");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Exports a simple PDF summary report and opens share sheet. */
    public static void exportPdf(Context ctx, List<SpeedTestResult> results) {
        try {
            PdfDocument pdf = new PdfDocument();
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas c = page.getCanvas();

            Paint title = new Paint();
            title.setTextSize(20f);
            title.setFakeBoldText(true);
            c.drawText("Internet Speed Meter — Speed Report", 40, 60, title);

            Paint sub = new Paint();
            sub.setTextSize(12f);
            sub.setColor(0xFF555555);
            c.drawText("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(new Date()), 40, 85, sub);

            Paint header = new Paint();
            header.setTextSize(13f);
            header.setFakeBoldText(true);
            c.drawText("Date", 40, 130, header);
            c.drawText("Avg Speed", 270, 130, header);
            c.drawText("Duration", 430, 130, header);

            Paint line = new Paint();
            line.setStrokeWidth(1f);
            line.setColor(0xFFCCCCCC);
            c.drawLine(40, 138, 555, 138, line);

            Paint row = new Paint();
            row.setTextSize(11f);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            int y = 158;
            int shown = 0;
            for (SpeedTestResult r : results) {
                if (y > 800) break;
                c.drawText(sdf.format(new Date(r.timestamp)), 40, y, row);
                c.drawText(String.format(Locale.getDefault(), "%.2f MB/s", r.speedMbps), 270, y, row);
                c.drawText(r.durationSec + "s", 430, y, row);
                y += 22;
                shown++;
            }

            // Average
            double avg = results.isEmpty() ? 0 :
                    results.stream().mapToDouble(r -> r.speedMbps).average().orElse(0);
            Paint foot = new Paint();
            foot.setTextSize(12f);
            foot.setFakeBoldText(true);
            c.drawLine(40, y + 10, 555, y + 10, line);
            c.drawText(String.format(Locale.getDefault(),
                    "Overall Avg: %.2f MB/s across %d tests", avg, shown), 40, y + 30, foot);

            pdf.finishPage(page);

            File dir = new File(ctx.getCacheDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, "speed_report_" + timestamp() + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                pdf.writeTo(fos);
            }
            pdf.close();

            share(ctx, file, "application/pdf");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void share(Context ctx, File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(ctx, AUTHORITY, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(intent, "Share Report"));
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date());
    }
}
