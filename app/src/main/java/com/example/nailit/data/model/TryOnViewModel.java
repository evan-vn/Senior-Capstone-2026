package com.example.nailit.data.model;

import androidx.lifecycle.ViewModel;

import java.util.List;
import java.util.Set;

public class TryOnViewModel extends ViewModel {
    public List<Polish> cachedPolishes = null;
    public Set<String> cachedUids = null;
}