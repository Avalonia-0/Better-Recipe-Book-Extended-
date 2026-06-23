package com.alonie.brbe.pipeline;

/**
 * One stage in the {@code updateCollections} pipeline.
 *
 * <p>Stages execute in a fixed, documented order.  Each stage receives the
 * mutable {@link PipelineContext} and decides whether to modify state or
 * signal that downstream stages should be skipped.
 *
 * <p>Return {@code true} to continue to the next stage, {@code false} to
 * skip remaining stages.  (The final PageUpdate and CacheSave stages
 * should always run — they are called explicitly at the end.)
 */
@FunctionalInterface
public interface PipelineStage {
    /**
     * Execute this stage.
     * @return true to continue pipeline, false to skip remaining data stages
     */
    boolean process(PipelineContext ctx);
}
