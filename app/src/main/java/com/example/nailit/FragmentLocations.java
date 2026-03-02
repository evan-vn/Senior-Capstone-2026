package com.example.nailit;

import android.Manifest;
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
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.CircularBounds;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.api.net.SearchNearbyRequest;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) getUserLocationAndLoadNearby();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_salons, container, false);

        searchLocationInput = view.findViewById(R.id.searchLocationInput);
        useMyLocationBtn = view.findViewById(R.id.useMyLocationBtn);
        salonCount = view.findViewById(R.id.salonCount);
        salonRecyclerView = view.findViewById(R.id.salonRecyclerView);

        // RecyclerView setup (RecyclerView is inside ScrollView -> disable nested scrolling)
        adapter = new SalonAdapter(salonItems, this::onSalonClicked);
        salonRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        salonRecyclerView.setAdapter(adapter);
        salonRecyclerView.setNestedScrollingEnabled(false);

        // Initialize Places (NEW Places API enabled for Nearby Search)
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(
                    requireContext(),
                    getString(R.string.google_maps_key)
            );
        }
        placesClient = Places.createClient(requireContext());

        // Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        hookSearchBoxAutocomplete();
        hookUseMyLocationButton();

        updateCount();
        return view;
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
                        .build();

        placesClient.findAutocompletePredictions(req)
                .addOnSuccessListener(response -> {
                    List<AutocompletePrediction> predictions = response.getAutocompletePredictions();
                    salonItems.clear();

                    int limit = Math.min(8, predictions.size());
                    if (limit == 0) {
                        adapter.notifyDataSetChanged();
                        updateCount();
                        return;
                    }

                    // Fetch details for each prediction (name/address/rating/latlng)
                    for (int i = 0; i < limit; i++) {
                        fetchPlaceDetailsAndAdd(predictions.get(i).getPlaceId());
                    }
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
                });
    }

    // 2) USE MY LOCATION -> permission -> get location -> Nearby Search
    private void hookUseMyLocationButton() {
        useMyLocationBtn.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                getUserLocationAndLoadNearby();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
    }

    private void getUserLocationAndLoadNearby() {

        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) return;
                    loadNearbySalons(location);
                })
                .addOnFailureListener(e -> {
                });
    }
    private void loadNearbySalons(@NonNull Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        // Nearby Search:
        // 1) location restriction  2) field list  3) included types
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.RATING,
                Place.Field.LAT_LNG
        );

        LatLng center = new LatLng(lat, lng);
        CircularBounds circle = CircularBounds.newInstance(center, 5000); // 5km radius

        // Use both types to get more results (Google place types)
        List<String> includedTypes = Arrays.asList("nail_salon", "beauty_salon");

        SearchNearbyRequest req = SearchNearbyRequest.builder(circle, placeFields)
                .setIncludedTypes(includedTypes)
                .setMaxResultCount(20)
                .build();

        placesClient.searchNearby(req)
                .addOnSuccessListener(response -> {
                    List<Place> places = response.getPlaces();

                    salonItems.clear();
                    for (Place p : places) {
                        String name = (p.getName() != null) ? p.getName() : "Unknown";
                        String address = (p.getAddress() != null) ? p.getAddress() : "";
                        Double rating = p.getRating();
                        salonItems.add(new SalonItem(p.getId(), name, address, rating));
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

    private void updateCount() {
        salonCount.setText(salonItems.size() + " salons found");
    }


    // Minimal model + adapter
    public static class SalonItem {
        public final String placeId;
        public final String name;
        public final String address;
        public final Double rating;

        public SalonItem(String placeId, String name, String address, Double rating) {
            this.placeId = placeId;
            this.name = name;
            this.address = address;
            this.rating = rating;
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
                .addOnSuccessListener(resp -> showDetailsBottomSheet(resp.getPlace()))
                .addOnFailureListener(e -> {

                });
    }

    private void showDetailsBottomSheet(Place p) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.bottom_sheet_salon, null);

        TextView name = v.findViewById(R.id.bsName);
        TextView rating = v.findViewById(R.id.bsRating);
        TextView address = v.findViewById(R.id.bsAddress);
        TextView phone = v.findViewById(R.id.bsPhone);
        TextView open = v.findViewById(R.id.bsOpen);

        name.setText(p.getName() != null ? p.getName() : "Salon");
        rating.setText(p.getRating() != null ? "⭐ " + p.getRating() : "⭐ N/A");
        address.setText(p.getAddress() != null ? p.getAddress() : "");
        phone.setText(p.getPhoneNumber() != null ? p.getPhoneNumber() : "Phone: N/A");

        if (p.getOpeningHours() != null && p.getOpeningHours().getWeekdayText() != null) {
            open.setText(p.getOpeningHours().getWeekdayText().get(0)); // simple display
        } else {
            open.setText("Hours: N/A");
        }

        dialog.setContentView(v);
        dialog.show();
    }


}