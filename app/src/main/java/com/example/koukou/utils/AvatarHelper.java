package com.example.koukou.utils;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.example.koukou.R;

public class AvatarHelper {
    public static void loadAvatar(ImageView imageView, String avatar) {
        Context context = imageView.getContext();
        
        if (avatar == null || avatar.isEmpty()) {
            avatar = "ic_avatar_1";
        }
        
        if (avatar.startsWith("/") || avatar.startsWith("file://") || avatar.startsWith("content://")) {
            // Local file path or URI
            Glide.with(context)
                 .load(avatar)
                 .transform(new CircleCrop())
                 .placeholder(R.mipmap.tubiao)
                 .into(imageView);
        } else {
            // Resource name like "ic_avatar_1"
            int resId = context.getResources().getIdentifier(avatar, "drawable", context.getPackageName());
            if (resId == 0) {
                resId = context.getResources().getIdentifier(avatar, "mipmap", context.getPackageName());
            }
            if (resId != 0) {
                Glide.with(context)
                     .load(resId)
                     .transform(new CircleCrop())
                     .into(imageView);
            } else {
                Glide.with(context)
                     .load(R.mipmap.tubiao)
                     .transform(new CircleCrop())
                     .into(imageView);
            }
        }
    }
}
