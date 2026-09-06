package com.example.hackathon;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.hackathon.models.NotificationItem;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private LinearLayout notificationContainer;
    private LinearLayout emptyState;

    private List<NotificationItem> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_notifications);

        backButton = findViewById(R.id.backButton);
        notificationContainer = findViewById(R.id.notificationContainer);
        emptyState = findViewById(R.id.emptyState);

        backButton.setOnClickListener(v -> finish());

        loadSampleNotifications();
        renderNotifications();
    }

    private void loadSampleNotifications() {
        notifications.add(new NotificationItem(
                "Obstacle still there",
                "Another user confirmed the obstacle at the Library is still there.",
                "2h ago",
                true
        ));

        notifications.add(new NotificationItem(
                "Obstacle nearby",
                "A blocked tactile path was reported 200m from your last location.",
                "5h ago",
                true
        ));

        notifications.add(new NotificationItem(
                "Obstacle cleared",
                "Community marked the Persiaran Newron obstacle as not there.",
                "Yesterday",
                false
        ));

        notifications.add(new NotificationItem(
                "Trust level updated",
                "Your trust level increased to HIGH based on recent activity.",
                "3 days ago",
                false
        ));
    }

    private void renderNotifications() {
        notificationContainer.removeAllViews();

        if (notifications.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }

        emptyState.setVisibility(View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);

        for (NotificationItem item : notifications) {
            View itemView = inflater.inflate(R.layout.item_notification, notificationContainer, false);

            TextView titleView = itemView.findViewById(R.id.notifTitle);
            TextView subtitleView = itemView.findViewById(R.id.notifSubtitle);
            TextView timeView = itemView.findViewById(R.id.notifTime);
            View unreadDot = itemView.findViewById(R.id.unreadDot);

            titleView.setText(item.getTitle());
            subtitleView.setText(item.getSubtitle());
            timeView.setText(item.getTimeAgo());

            unreadDot.setVisibility(item.isUnread() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                item.markRead();
                renderNotifications();
            });

            notificationContainer.addView(itemView);
        }
    }
}