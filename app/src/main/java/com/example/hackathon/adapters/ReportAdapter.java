package com.example.hackathon.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.hackathon.R;
import com.example.hackathon.models.AccessibilityReport;

import java.util.List;

public class ReportAdapter
        extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    public interface OnReportClickListener {
        void onReportClick(AccessibilityReport report);
    }

    private List<AccessibilityReport> reports;
    private OnReportClickListener listener;

    public ReportAdapter(List<AccessibilityReport> reports) {
        this.reports = reports;
    }

    public void setOnReportClickListener(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setReports(List<AccessibilityReport> reports) {
        this.reports = reports;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.report_item, parent, false);

        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ReportViewHolder holder,
            int position) {

        AccessibilityReport report = reports.get(position);

        holder.locationText.setText(report.getLocationName());
        holder.issueText.setText(report.getIssueType());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReportClick(report);
            }
        });
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    public static class ReportViewHolder
            extends RecyclerView.ViewHolder {

        TextView locationText;
        TextView issueText;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);

            locationText = itemView.findViewById(R.id.locationText);
            issueText = itemView.findViewById(R.id.issueText);
        }
    }
}
