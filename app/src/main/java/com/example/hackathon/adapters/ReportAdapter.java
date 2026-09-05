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

    private List<AccessibilityReport> reports;

    public ReportAdapter(List<AccessibilityReport> reports) {
        this.reports = reports;
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

        holder.confirmationText.setText(
                "Yes " + report.getConfirmations()
        );

        holder.disputeText.setText(
                "Disagree " + report.getDisputes()
        );
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    public static class ReportViewHolder
            extends RecyclerView.ViewHolder {

        TextView locationText;
        TextView issueText;
        TextView confirmationText;
        TextView disputeText;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);

            locationText = itemView.findViewById(R.id.locationText);
            issueText = itemView.findViewById(R.id.issueText);
            confirmationText =
                    itemView.findViewById(R.id.confirmationText);
            disputeText =
                    itemView.findViewById(R.id.disputeText);
        }
    }
}