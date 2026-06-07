package com.example.graduationalbum;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {
    public interface OnPhotoClickListener {
        void onPhotoClick(Photo photo);
    }

    private final List<Photo> photos;
    private final OnPhotoClickListener listener;

    public PhotoAdapter(List<Photo> photos, OnPhotoClickListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Photo photo = photos.get(position);
        holder.title.setText(photo.getTitle() != null ? photo.getTitle() : "毕业相册");
        holder.author.setText(photo.getAuthor() != null ? photo.getAuthor() : "机器人2202班");
        Glide.with(holder.image.getContext())
                .load(photo.getThumb() != null ? photo.getThumb() : photo.getSrc())
                .centerCrop()
                .into(holder.image);
        holder.itemView.setOnClickListener(v -> listener.onPhotoClick(photo));
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView title;
        final TextView author;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.imagePhoto);
            title = itemView.findViewById(R.id.textPhotoTitle);
            author = itemView.findViewById(R.id.textPhotoAuthor);
        }
    }
}
