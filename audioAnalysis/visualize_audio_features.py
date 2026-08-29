from __future__ import annotations

import os
import webbrowser

import numpy as np
import pandas as pd
import plotly.express as px

from supabase import create_client
from dotenv import load_dotenv
from sklearn.preprocessing import StandardScaler
import umap


# ============================================================
# Configuration
# ============================================================

PROJECT_ROOT = os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))
)

load_dotenv(
    os.path.join(PROJECT_ROOT, ".env")
)

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_KEY"]

TABLE_NAME = "audio_features"


# ============================================================
# Feature groups
# ============================================================

ALL_FEATURES = [
    "loudness_lufs",
    "rms_db",
    "spectral_centroid_hz",
    "spectral_flux",
    "bpm",
    "onset_density",
    "sub_bass_ratio",
    "bass_ratio",
    "low_mid_ratio",
    "mid_ratio",
    "high_mid_ratio",
    "high_ratio",
]


FREQUENCY_FEATURES = [
    "sub_bass_ratio",
    "bass_ratio",
    "low_mid_ratio",
    "mid_ratio",
    "high_mid_ratio",
    "high_ratio",
]


RHYTHM_FEATURES = [
    "bpm",
    "onset_density",
]


ENERGY_TIMBRE_FEATURES = [
    "loudness_lufs",
    "rms_db",
    "spectral_centroid_hz",
    "spectral_flux",
]


# ============================================================
# Supabase
# ============================================================

def load_features() -> pd.DataFrame:

    supabase = create_client(
        SUPABASE_URL,
        SUPABASE_KEY,
    )

    response = (
        supabase
        .table(TABLE_NAME)
        .select(
            "song_id,"
            + ",".join(ALL_FEATURES)
        )
        .execute()
    )

    if not response.data:
        raise RuntimeError(
            "No rows were returned from audio_features."
        )

    return pd.DataFrame(response.data)


# ============================================================
# Prepare data
# ============================================================

def prepare_features(
        df: pd.DataFrame,
        feature_columns: list[str],
) -> tuple[pd.DataFrame, np.ndarray]:

    required = [
        "song_id",
        *feature_columns,
    ]

    missing = [
        column
        for column in required
        if column not in df.columns
    ]

    if missing:
        raise RuntimeError(
            "Missing columns: "
            + ", ".join(missing)
        )

    data = df[
        required
    ].copy()

    for column in feature_columns:
        data[column] = pd.to_numeric(
            data[column],
            errors="coerce",
        )

    before = len(data)

    data = data.dropna(
        subset=feature_columns
    ).copy()

    removed = before - len(data)

    if removed:
        print(
            f"Removed {removed} rows "
            f"because of missing values."
        )

    if len(data) < 3:
        raise RuntimeError(
            "Not enough valid songs."
        )

    X = data[
        feature_columns
    ].to_numpy(
        dtype=np.float64
    )

    return data, X


# ============================================================
# UMAP
# ============================================================

def calculate_umap(
        X: np.ndarray,
) -> np.ndarray:

    scaler = StandardScaler()

    X_scaled = scaler.fit_transform(X)

    n_neighbors = min(
        15,
        max(2, len(X) - 1),
    )

    reducer = umap.UMAP(
        n_components=2,
        n_neighbors=n_neighbors,
        min_dist=0.1,
        metric="euclidean",
        random_state=42,
    )

    return reducer.fit_transform(
        X_scaled
    )


# ============================================================
# Plot
# ============================================================

def make_umap_plot(
        df: pd.DataFrame,
        coordinates: np.ndarray,
        title: str,
        color_feature: str,
        color_label: str,
):
    plot_df = pd.DataFrame({
        "song_id": df["song_id"].astype(str),
        "umap_1": coordinates[:, 0],
        "umap_2": coordinates[:, 1],
    })
    for feature in df.columns:
        if feature != "song_id":
            plot_df[feature] = df[feature].to_numpy()

    # Add the feature used for coloring.
    plot_df[color_label] = df[
        color_feature
    ].to_numpy()

    fig = px.scatter(
    plot_df,
    x="umap_1",
    y="umap_2",
    color=color_label,
    hover_name="song_id",
    hover_data=[
    column
    for column in df.columns
    if column != "song_id"
    ],
    title=title,
    labels={
        "umap_1": "UMAP 1",
        "umap_2": "UMAP 2",
        color_label: color_label,
    },
)

    fig.update_traces(
        marker=dict(
            size=8,
            opacity=0.8,
        )
    )

    fig.update_layout(
        width=1000,
        height=800,
    )

    return fig


# ============================================================
# Generate one UMAP
# ============================================================

def generate_umap(
        df: pd.DataFrame,
        features: list[str],
        title: str,
        color_feature: str,
        color_label: str,
        filename: str,
):

    print(
        f"Calculating {title}..."
    )

    data, X = prepare_features(
        df,
        features,
    )

    coordinates = calculate_umap(X)

    fig = make_umap_plot(
        data,
        coordinates,
        title,
        color_feature,
        color_label,
    )

    path = os.path.abspath(filename)

    fig.write_html(
        path,
        include_plotlyjs=True,
    )

    print(
        f"  → {path}"
    )

    webbrowser.open(
        "file://" + path
    )


# ============================================================
# Main
# ============================================================

def main():

    print(
        "Loading audio features..."
    )

    df = load_features()

    print(
        f"Loaded {len(df)} songs."
    )

    print()

    # --------------------------------------------------------
    # 1. ALL FEATURES
    # --------------------------------------------------------

    generate_umap(
        df=df,
        features=ALL_FEATURES,
        title="Audio Features — All Features",
        color_feature="bpm",
        color_label="BPM",
        filename="audio_features_umap_all.html",
    )

    # --------------------------------------------------------
    # 2. FREQUENCY PROFILE
    # --------------------------------------------------------

    generate_umap(
        df=df,
        features=FREQUENCY_FEATURES,
        title="Audio Features — Frequency Profile",
        color_feature="bass_ratio",
        color_label="Bass Ratio",
        filename="audio_features_umap_frequency.html",
    )

    # --------------------------------------------------------
    # 3. RHYTHM
    # --------------------------------------------------------

    generate_umap(
        df=df,
        features=RHYTHM_FEATURES,
        title="Audio Features — Rhythm",
        color_feature="bpm",
        color_label="BPM",
        filename="audio_features_umap_rhythm.html",
    )

    # --------------------------------------------------------
    # 4. ENERGY / TIMBRE
    # --------------------------------------------------------

    generate_umap(
        df=df,
        features=ENERGY_TIMBRE_FEATURES,
        title="Audio Features — Energy / Timbre",
        color_feature="spectral_centroid_hz",
        color_label="Spectral Centroid (Hz)",
        filename="audio_features_umap_energy_timbre.html",
    )

    print()
    print("Done.")


if __name__ == "__main__":
    main()