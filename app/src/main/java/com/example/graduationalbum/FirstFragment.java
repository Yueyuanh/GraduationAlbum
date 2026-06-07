package com.example.graduationalbum;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.graduationalbum.databinding.FragmentFirstBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private final List<Photo> photos = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recyclerView.setAdapter(new PhotoAdapter(photos, this::navigateToDetail));
        loadPhotos();
    }

    private void loadPhotos() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.textError.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);

        executor.execute(() -> {
            try {
                List<Photo> result = ApiClient.fetchPhotos(requireContext());
                mainHandler.post(() -> showPhotos(result));
            } catch (IOException e) {
                mainHandler.post(() -> showError(e.getMessage()));
            }
        });
    }

    private void showPhotos(List<Photo> result) {
        photos.clear();
        photos.addAll(result);
        binding.recyclerView.getAdapter().notifyDataSetChanged();
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        binding.progressBar.setVisibility(View.GONE);
        binding.recyclerView.setVisibility(View.GONE);
        binding.textError.setVisibility(View.VISIBLE);
        binding.textError.setText(message != null ? message : getString(R.string.load_error));
    }

    private void navigateToDetail(Photo photo) {
        Bundle args = new Bundle();
        args.putString("photo_id", photo.getId());
        args.putString("photo_src", photo.getSrc());
        args.putString("photo_thumb", photo.getThumb());
        args.putString("photo_title", photo.getTitle());
        args.putString("photo_author", photo.getAuthor());
        args.putString("photo_created_at", photo.getCreatedAt());
        args.putString("photo_media_type", photo.getMediaType());
        NavHostFragment.findNavController(FirstFragment.this)
                .navigate(R.id.action_FirstFragment_to_SecondFragment, args);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        executor.shutdownNow();
    }
}
