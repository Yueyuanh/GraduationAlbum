package com.example.graduationalbum;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.graduationalbum.databinding.FragmentSecondBinding;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentSecondBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            String title = args.getString("photo_title");
            String author = args.getString("photo_author");
            String createdAt = args.getString("photo_created_at");
            String mediaType = args.getString("photo_media_type");
            String src = args.getString("photo_src");

            binding.textTitle.setText(title != null ? title : getString(R.string.app_name));
            binding.textAuthor.setText(getString(R.string.photo_author_template, author != null ? author : "机器人2202班"));
            binding.textCreatedAt.setText(getString(R.string.photo_date_template, createdAt != null ? createdAt : "未知"));
            binding.textType.setText(getString(R.string.photo_type_template, mediaType != null ? mediaType : "图片"));
            binding.textDescription.setText(getString(R.string.photo_detail_description));

            Glide.with(this)
                    .load(src)
                    .centerCrop()
                    .into(binding.imageDetail);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
