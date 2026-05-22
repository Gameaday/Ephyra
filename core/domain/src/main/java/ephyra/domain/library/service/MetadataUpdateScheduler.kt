package ephyra.domain.library.service

interface MetadataUpdateScheduler {
    /**
     * Enqueues a one-off metadata refresh for all library manga.
     *
     * @return `true` if the job was successfully enqueued, `false` if a run is already in progress.
     */
    fun startMetadataUpdateNow(): Boolean
}
