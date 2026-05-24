package ephyra.source.api

import ephyra.domain.content.model.ContentItem

/**
 * Declares the underlying transport type of the media source.
 */
enum class TransportType {
    LOCAL_FS,
    SFTP,
    SMB,
    FTP,
    HTTP_RULE_BASED,
}

/**
 * Base interface for all modern, structured media sources in Ephyra.
 */
interface SourceHierarchy : ContentSource {
    val transportType: TransportType
}

/**
 * Interface for directly scanning local filesystems and memory cards.
 */
interface LocalFsSource : SourceHierarchy {
    suspend fun scanDirectory(absolutePath: String): List<ContentItem>
}

/**
 * Interface for remote network file share transports (SMB, FTP, SFTP).
 */
interface NetworkSource : SourceHierarchy {
    val connectionString: String
    suspend fun testConnection(): Boolean
    suspend fun fetchRemoteDirectory(remotePath: String): List<ContentItem>
}

/**
 * Interface for dynamic, scriptable scraping using runtime WASM or QuickJS rules.
 */
interface HttpScriptableSource : SourceHierarchy {
    val baseUrl: String
    val scriptRule: String?
    suspend fun evaluateRule(script: String, params: Map<String, String>): String
}
