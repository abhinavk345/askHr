package com.intech.ai.utility;

import java.util.List;

public class CosineSimilarityUtil {

    public static double similarity(List<Float> v1, List<Float> v2) {

        if (v1.size() != v2.size()) return 0.0;

        double dot = 0.0, normA = 0.0, normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            dot += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
