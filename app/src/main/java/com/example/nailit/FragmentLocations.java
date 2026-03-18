package com.example.nailit;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nailit.data.api.SavedSalonApi;
import com.example.nailit.data.network.ApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.TypeFilter;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.widget.Toast;
import com.example.nailit.data.model.SavedSalonRequest;
import com.example.nailit.data.model.SavedSalonResponse;

import com.example.nailit.data.network.TokenStore;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class FragmentLocations extends Fragment {

    private TextInputEditText searchLocationInput;
    private View useMyLocationBtn;
    private TextView salonCount;
    private RecyclerView salonRecyclerView;

    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationClient;

    private final ArrayList<SalonItem> salonItems = new ArrayList<>();
    private SalonAdapter adapter;

    // Debounce typing
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private SavedSalonApi savedSalonApi;

    private TokenStore tokenStore;
    private String currentUserId;
    private final Set<String> savedPlaceIds = new HashSet<>();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    getUserLocationAndLoadNearby();
                } else {
                    Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_salons, container, false);

        tokenStore = new TokenStore(requireContext());
        currentUserId = tokenStore.getUserId();
        savedSalonApi = ApiClient.getInstance(tokenStore).create(SavedSalonApi.class);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        searchLocationInput = view.findViewById(R.id.searchLocationInput);
        useMyLocationBtn = view.findViewById(R.id.useMyLocationBtn);
        salonCount = view.findViewById(R.id.salonCount);
        salonRecyclerView = view.findViewById(R.id.salonRecyclerView);

        adapter = new SalonAdapter(salonItems, this::onSalonClicked);
        salonRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        salonRecyclerView.setAdapter(adapter);
        salonRecyclerView.setNestedScrollingEnabled(false);

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                    requireContext(),
                    "AIzaSyC3OXO9oWOu9f7AdODNt-YJ5ZPctywfVwI"
            );
        }
        placesClient = Places.createClient(requireContext());

        hookSearchBoxAutocomplete();
        hookUseMyLocationButton();
        loadSavedSalons();

        return view;
    }

    private void loadSavedSalons() {
        if (currentUserId == null || currentUserId.trim().isEmpty()) return;

        savedSalonApi.getSavedSalons("eq." + currentUserId, "created_at.desc")
                .enqueue(new Callback<List<SavedSalonResponse>>() {
                    @Override
                    public void onResponse(Call<List<SavedSalonResponse>> call,
                                           Response<List<SavedSalonResponse>> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            savedPlaceIds.clear();
                            for (SavedSalonResponse row : response.body()) {
                                if (row != null && row.getPlaceId() != null) {
                                    savedPlaceIds.add(row.getPlaceId());
                                }
                            }
                            syncSavedFlagsToVisibleList();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<SavedSalonResponse>> call, Throwable t) {
                        // no-op
                    }
                });
    }

    private void syncSavedFlagsToVisibleList() {
        for (SalonItem item : salonItems) {
            item.isSaved = savedPlaceIds.contains(item.placeId);
        }
        adapter.notifyDataSetChanged();
        updateCount();
    }

    // 1) SEARCH BOX -> autocomplete predictions
    private void hookSearchBoxAutocomplete() {
        searchLocationInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                final String query = (s == null) ? "" : s.toString().trim();

                if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);

                pendingSearch = () -> {
                    if (query.length() < 2) {
                        salonItems.clear();
                        adapter.notifyDataSetChanged();
                        updateCount();
                        return;
                    }
                    searchPredictions(query);
                };

                debounceHandler.postDelayed(pendingSearch, 350);
            }
        });
    }

    private void searchPredictions(String query) {
        FindAutocompletePredictionsRequest req =
                FindAutocompletePredictionsRequest.builder()
                        .setQuery(query)
                        .setTypeFilter(TypeFilter.CITIES)
                        .setCountries("US")
                        .build();

        placesClient.findAutocompletePredictions(req)
                .addOnSuccessListener(response -> {
                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                    if (predictions.isEmpty()) {
                        salonItems.clear();
                        adapter.notifyDataSetChanged();
                        updateCount();
                        return;
                    }

                    // Auto-select the first city suggestion and load salons near it:
                    fetchCityLatLngAndLoadNearby(predictions.get(0).getPlaceId());
                })
                .addOnFailureListener(e -> {
                    salonItems.clear();
                    adapter.notifyDataSetChanged();
                    updateCount();
                });
    }

    private void fetchCityLatLngAndLoadNearby(String placeId) {
        List<Place.Field> fields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG);

        FetchPlaceRequest req = FetchPlaceRequest.builder(placeId, fields).build();

        placesClient.fetchPlace(req)
                .addOnSuccessListener(resp -> {
                    Place city = resp.getPlace();
                    LatLng latLng = city.getLatLng();
                    if (latLng == null) return;

                    // Run the same Nearby Search but centered on the CITY
                    loadNearbySalonsFromLatLng(latLng, 5000); // 5km radius
                })
                .addOnFailureListener(e -> {
                    salonItems.clear();
                    adapter.notifyDataSetChanged();
                    updateCount();
                });
    }

    private void loadNearbySalonsFromLatLng(@NonNull LatLng center, double radiusMeters) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.LAT_LNG
        );

        CircularBounds circle = CircularBounds.newInstance(center, radiusMeters);

        SearchNearbyRequest req = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(Arrays.asList("nail_salon", "beauty_salon"))
                .setMaxResultCount(20)
                .build();

        placesClient.searchNearby(req)
                .addOnSuccessListener(response -> {
                    salonItems.clear();
                    for (Place p : response.getPlaces()) {
                        String id = p.getId() != null ? p.getId() : "";
                        String name = p.getName() != null ? p.getName() : "Unknown";
                        String address = p.getAddress() != null ? p.getAddress() : "";
                        Double rating = p.getRating();
                        SalonItem item = new SalonItem(id, name, address, rating);
                        item.isSaved = savedPlaceIds.contains(id);
                        salonItems.add(item);
                    }

                    adapter.notifyDataSetChanged();
                    updateCount();
                })
                .addOnFailureListener(e -> {
                    salonItems.clear();
                    adapter.notifyDataSetChanged();
                    updateCount();
                });
    }
    private void fetchPlaceDetailsAndAdd(String placeId) {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.LAT_LNG
        );

        FetchPlaceRequest req = FetchPlaceRequest.builder(placeId, fields).build();

        placesClient.fetchPlace(req)
                .addOnSuccessListener(response -> {
                    Place p = response.getPlace();

                    String name = (p.getName() != null) ? p.getName() : "Unknown";
                    String address = (p.getAddress() != null) ? p.getAddress() : "";
                    Double rating = p.getRating();

                    salonItems.add(new SalonItem(placeId, name, address, rating));
                    adapter.notifyDataSetChanged();
                    updateCount();
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }

    // 2) USE MY LOCATION -> permission -> get location -> Nearby Search
    private void hookUseMyLocationButton() {
        useMyLocationBtn.setOnClickListener(v -> {
            if (!isAdded()) return;

            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                getUserLocationAndLoadNearby();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
    }
    private void getUserLocationAndLoadNearby() {
        if (!isAdded() || getContext() == null) return;

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (!isAdded() || getView() == null) return;

                    if (location == null) {
                        salonItems.clear();
                        adapter.notifyDataSetChanged();
                        updateCount();
                        return;
                    }

                    loadNearbySalons(location);
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                });
    }
    private void loadNearbySalons(Location location) {
        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        CircularBounds bounds = CircularBounds.newInstance(userLatLng, 5000.0);

        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.RATING
        );

        SearchNearbyRequest request = SearchNearbyRequest.builder(bounds, fields)
                .setIncludedTypes(Arrays.asList("nail_salon", "beauty_salon"))
                .setMaxResultCount(20)
                .build();

        placesClient.searchNearby(request)
                .addOnSuccessListener(response -> {
                    salonItems.clear();

                    for (Place place : response.getPlaces()) {
                        String id = place.getId() != null ? place.getId() : "";
                        String name = place.getName() != null ? place.getName() : "Unknown salon";
                        String address = place.getAddress() != null ? place.getAddress() : "No address";
                        Double rating = place.getRating() != null ? place.getRating() : 0.0;

                        SalonItem item = new SalonItem(id, name, address, rating);
                        item.isSaved = savedPlaceIds.contains(id);
                        salonItems.add(item);
                    }

                    adapter.notifyDataSetChanged();
                    updateCount();
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Nearby search failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocationAndLoadNearby();
            } else {
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateCount() {
        salonCount.setText(salonItems.size() + " salons found");
    }


    // Minimal model + adapter
    public static class SalonItem {
        public final String placeId;
        public final String name;
        public final String address;
        public final Double rating;
        public boolean isSaved;

        public SalonItem(String placeId, String name, String address, Double rating) {
            this.placeId = placeId;
            this.name = name;
            this.address = address;
            this.rating = rating;
            this.isSaved = false;
        }
    }

    static class SalonAdapter extends RecyclerView.Adapter<SalonViewHolder> {

        interface OnSalonClickListener {
            void onClick(SalonItem item);
        }

        private final List<SalonItem> items;

        private final OnSalonClickListener listener;

        SalonAdapter(List<SalonItem> items, OnSalonClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public SalonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_salon, parent, false);
            return new SalonViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SalonViewHolder holder, int position) {
            SalonItem it = items.get(position);

            holder.salonName.setText(it.name);
            holder.salonAddress.setText(it.address == null ? "" : it.address);
            holder.itemView.setOnClickListener(v -> listener.onClick(items.get(position)));
            if (it.rating != null) {
                holder.salonRating.setText("⭐ " + String.format("%.1f", it.rating));
            } else {
                holder.salonRating.setText("⭐ N/A");
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static class SalonViewHolder extends RecyclerView.ViewHolder {

        final TextView salonName;
        final TextView salonRating;
        final TextView salonAddress;

        SalonViewHolder(@NonNull View itemView) {
            super(itemView);

            // connect to item_salon.xml IDs
            salonName = itemView.findViewById(R.id.salonName);
            salonRating = itemView.findViewById(R.id.salonRating);
            salonAddress = itemView.findViewById(R.id.salonAddress);
        }
    }

    private void onSalonClicked(SalonItem item) {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.PHONE_NUMBER,
                Place.Field.WEBSITE_URI,
                Place.Field.OPENING_HOURS
        );

        FetchPlaceRequest req = FetchPlaceRequest.builder(item.placeId, fields).build();

        placesClient.fetchPlace(req)
                .addOnSuccessListener(resp -> showDetailsBottomSheet(item, resp.getPlace()))
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Failed to load salon details", Toast.LENGTH_SHORT).show()
                );
    }

    private void showDetailsBottomSheet(SalonItem item, Place p) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.bottom_sheet_salon, null);

        TextView name = v.findViewById(R.id.bsName);
        TextView rating = v.findViewById(R.id.bsRating);
        TextView address = v.findViewById(R.id.bsAddress);
        TextView phone = v.findViewById(R.id.bsPhone);
        TextView open = v.findViewById(R.id.bsOpen);
        ImageButton btnHeart = v.findViewById(R.id.btnHeart);

        name.setText(p.getName() != null ? p.getName() : "Salon");
        rating.setText(p.getRating() != null ? "⭐ " + String.format("%.1f", p.getRating()) : "⭐ N/A");
        address.setText(p.getAddress() != null ? p.getAddress() : "Address: N/A");
        phone.setText(p.getPhoneNumber() != null ? p.getPhoneNumber() : "Phone: N/A");

        if (p.getOpeningHours() != null &&
                p.getOpeningHours().getWeekdayText() != null &&
                !p.getOpeningHours().getWeekdayText().isEmpty()) {
            open.setText(p.getOpeningHours().getWeekdayText().get(0));
        } else {
            open.setText("Hours: N/A");
        }

        btnHeart.setImageResource(item.isSaved
                ? R.drawable.ic_heart_filled
                : R.drawable.ic_heart_outline);

        btnHeart.setOnClickListener(view -> {
            if (item.isSaved) {
                removeSavedSalon(item, btnHeart);
            } else {
                saveSalon(item, btnHeart);
            }
        });

        dialog.setContentView(v);
        dialog.show();
    }
    private void saveSalon(SalonItem item, ImageButton btnHeart) {
        if (!isAdded()) return;

        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "User not found. Please log in again.", Toast.LENGTH_LONG).show();
            return;
        }

        SavedSalonRequest request = new SavedSalonRequest(
                currentUserId,
                item.placeId,
                item.name,
                item.address != null ? item.address : "",
                item.rating != null ? item.rating : 0.0
        );

        Call<List<SavedSalonResponse>> call = savedSalonApi.saveSalon(request);
        android.util.Log.d("SAVE_SALON", "Request URL: " + call.request().url());

        call.enqueue(new Callback<List<SavedSalonResponse>>() {
            @Override
            public void onResponse(Call<List<SavedSalonResponse>> call,
                                   Response<List<SavedSalonResponse>> response) {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    item.isSaved = true;
                    savedPlaceIds.add(item.placeId);
                    btnHeart.setImageResource(R.drawable.ic_heart_filled);
                    Toast.makeText(requireContext(), "Salon saved", Toast.LENGTH_SHORT).show();
                    return;
                }

                String errorMessage = "Unknown error";
                try {
                    if (response.errorBody() != null) {
                        errorMessage = response.errorBody().string();
                    }
                } catch (Exception ignored) {}

                android.util.Log.e("SAVE_SALON", "HTTP " + response.code());
                android.util.Log.e("SAVE_SALON", "Error body: " + errorMessage);

                if (errorMessage.contains("JWT token has expired")) {
                    Toast.makeText(requireContext(), "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    tokenStore.clear();
                    ApiClient.reset();
                } else {
                    Toast.makeText(requireContext(), "Save failed: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<SavedSalonResponse>> call, Throwable t) {
                if (!isAdded()) return;
                android.util.Log.e("SAVE_SALON", "Failure", t);
                Toast.makeText(requireContext(), "Error saving salon: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    private void removeSavedSalon(SalonItem item, ImageButton btnHeart) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Please log in again", Toast.LENGTH_LONG).show();
            return;
        }

        savedSalonApi.removeSavedSalon("eq." + currentUserId, "eq." + item.placeId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (!isAdded()) return;

                        if (response.isSuccessful()) {
                            item.isSaved = false;
                            savedPlaceIds.remove(item.placeId);
                            btnHeart.setImageResource(R.drawable.ic_heart_outline);
                            Toast.makeText(requireContext(), "Salon removed", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Failed to remove salon", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Error removing salon", Toast.LENGTH_SHORT).show();
                    }
                });
    }


}