from __future__ import annotations

import os
import webbrowser

import numpy as np
import pandas as pd
import plotly.express as px

from supabase import create_client
from sklearn.preprocessing import StandardScaler
from sklearn.decomposition import PCA
import umap
from pathlib import Path
from dotenv import load_dotenv

PROJECT_ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = PROJECT_ROOT / ".env"

load_dotenv(ENV_FILE)


# ============================================================
# Configuration
# ============================================================

SUPABASE_URL = os.environ["SUPABASE_URL"]
SUPABASE_KEY = os.environ["SUPABASE_SERVICE_KEY"]

TABLE_NAME = "audio_features"


FEATURE_COLUMNS = [
    "loudness_lufs",
    "rms_db",
    "spectral_centroid_hz",
    "spectral_flux",
    "bpm",
    "onset_density",
    "beat_density",
    "sub_bass_ratio",
    "bass_ratio",
    "low_mid_ratio",
    "mid_ratio",
    "high_mid_ratio",
    "high_ratio",
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
            + ",".join(FEATURE_COLUMNS)
        )
        .execute()
    )

    if not response.data:
        raise RuntimeError(
            "No rows were returned from audio_features."
        )

    return pd.DataFrame(response.data)


# ============================================================
# Validation
# ============================================================

def prepare_features(
    df: pd.DataFrame,
) -> tuple[pd.DataFrame, np.ndarray]:

    missing_columns = [
        column
        for column in ["song_id", *FEATURE_COLUMNS]
        if column not in df.columns
    ]

    if missing_columns:
        raise RuntimeError(
            "Missing columns: "
            + ", ".join(missing_columns)
        )

    # Convert feature columns to numbers.
    for column in FEATURE_COLUMNS:
        df[column] = pd.to_numeric(
            df[column],
            errors="coerce",
        )

    # Remove rows containing invalid/missing values.
    before = len(df)

    df = df.dropna(
        subset=FEATURE_COLUMNS
    ).copy()

    removed = before - len(df)

    if removed:
        print(
            f"Removed {removed} rows containing "
            f"missing/invalid feature values."
        )

    if len(df) < 2:
        raise RuntimeError(
            "Not enough valid songs for visualization."
        )

    X = df[FEATURE_COLUMNS].to_numpy(
        dtype=np.float64
    )

    return df, X


# ============================================================
# PCA
# ============================================================

def calculate_pca(
    X: np.ndarray,
) -> tuple[np.ndarray, float, float]:

    scaler = StandardScaler()

    X_scaled = scaler.fit_transform(X)

    pca = PCA(
        n_components=2
    )

    coordinates = pca.fit_transform(
        X_scaled
    )

    explained = pca.explained_variance_ratio_

    return (
        coordinates,
        float(explained[0]),
        float(explained[1]),
    )


# ============================================================
# UMAP
# ============================================================

def calculate_umap(
    X: np.ndarray,
) -> np.ndarray:

    scaler = StandardScaler()

    X_scaled = scaler.fit_transform(X)

    # UMAP requires at least a few neighbors.
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
# Plotting
# ============================================================

def make_pca_plot(
    df: pd.DataFrame,
    coordinates: np.ndarray,
    explained_1: float,
    explained_2: float,
):
    plot_df = pd.DataFrame({
        "song_id": df["song_id"].astype(str),
        "x": coordinates[:, 0],
        "y": coordinates[:, 1],
    })

    fig = px.scatter(
        plot_df,
        x="x",
        y="y",
        hover_name="song_id",
        title=(
            "Audio Features — PCA"
        ),
        labels={
            "x": (
                f"PC1 "
                f"({explained_1 * 100:.1f}% variance)"
            ),
            "y": (
                f"PC2 "
                f"({explained_2 * 100:.1f}% variance)"
            ),
        },
    )

    fig.update_traces(
        marker=dict(
            size=7,
            opacity=0.75,
        )
    )

    return fig


def make_umap_plot(
    df: pd.DataFrame,
    coordinates: np.ndarray,
):
    plot_df = pd.DataFrame({
        "song_id": df["song_id"].astype(str),
        "x": coordinates[:, 0],
        "y": coordinates[:, 1],
    })

    fig = px.scatter(
        plot_df,
        x="x",
        y="y",
        hover_name="song_id",
        title="Audio Features — UMAP",
        labels={
            "x": "UMAP 1",
            "y": "UMAP 2",
        },
    )

    fig.update_traces(
        marker=dict(
            size=7,
            opacity=0.75,
        )
    )

    return fig


# ============================================================
# Main
# ============================================================

def main():

    print("Loading audio features...")

    df = load_features()

    print(
        f"Loaded {len(df)} songs."
    )

    df, X = prepare_features(df)

    print(
        f"Using {len(df)} valid songs."
    )

    # --------------------------------------------------------
    # PCA
    # --------------------------------------------------------

    print("Calculating PCA...")

    (
        pca_coordinates,
        explained_1,
        explained_2,
    ) = calculate_pca(X)

    pca_fig = make_pca_plot(
        df,
        pca_coordinates,
        explained_1,
        explained_2,
    )

    pca_file = "audio_features_pca.html"

    pca_fig.write_html(
        pca_file,
        include_plotlyjs=True,
    )

    # --------------------------------------------------------
    # UMAP
    # --------------------------------------------------------

    print("Calculating UMAP...")

    umap_coordinates = calculate_umap(X)

    umap_fig = make_umap_plot(
        df,
        umap_coordinates,
    )

    umap_file = "audio_features_umap.html"

    umap_fig.write_html(
        umap_file,
        include_plotlyjs=True,
    )

    # --------------------------------------------------------
    # Output
    # --------------------------------------------------------

    print()
    print("Done.")
    print()
    print(
        f"PCA variance explained:"
    )
    print(
        f"  PC1: {explained_1 * 100:.2f}%"
    )
    print(
        f"  PC2: {explained_2 * 100:.2f}%"
    )
    print()
    print(
        f"PCA:  {os.path.abspath(pca_file)}"
    )
    print(
        f"UMAP: {os.path.abspath(umap_file)}"
    )

    # Open both plots.
    webbrowser.open(
        "file://" + os.path.abspath(pca_file)
    )

    webbrowser.open(
        "file://" + os.path.abspath(umap_file)
    )


if __name__ == "__main__":
    main()