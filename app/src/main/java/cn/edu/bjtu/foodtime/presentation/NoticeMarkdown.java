package cn.edu.bjtu.foodtime.presentation;

import android.content.Intent;
import android.net.Uri;
import android.widget.TextView;
import io.noties.markwon.*;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.image.ImagesPlugin;

/** Native Markdown spans; no HTML/WebView or arbitrary intent/file links. */
final class NoticeMarkdown {
  static void render(TextView view, String content) {
    Markwon markwon = Markwon.builder(view.getContext())
        .usePlugin(TablePlugin.create(view.getContext()))
        .usePlugin(StrikethroughPlugin.create())
        .usePlugin(ImagesPlugin.create(plugin -> {
          plugin.removeSchemeHandler("file");
          plugin.removeSchemeHandler("data");
          plugin.removeSchemeHandler("content");
        }))
        .usePlugin(new AbstractMarkwonPlugin() {
          @Override public void configureConfiguration(MarkwonConfiguration.Builder builder) {
            builder.linkResolver((widget, link) -> {
              Uri uri=Uri.parse(link);
              String scheme=uri.getScheme();
              if(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme)&&!"mailto".equalsIgnoreCase(scheme)) return;
              try { widget.getContext().startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
              catch(android.content.ActivityNotFoundException ignored) { }
            });
          }
        }).build();
    view.setTextSize(16);
    view.setLineSpacing(Ui.dp(view.getContext(),4),1.15f);
    view.setLinkTextColor(Design.RED);
    markwon.setMarkdown(view,content==null?"":content);
  }
  private NoticeMarkdown() {}
}
