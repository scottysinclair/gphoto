package scott.gphoto

import com.google.api.gax.core.CredentialsProvider
import com.google.auth.oauth2.*
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import com.google.photos.library.v1.upload.UploadMediaItemRequest
import com.google.photos.library.v1.util.NewMediaItemFactory
import com.google.rpc.Code
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.io.*
import java.lang.IllegalStateException
import java.net.URI
import java.util.*

//https://developers.google.com/google-ads/api/docs/samples/authenticate-in-desktop-application
//https://developers.google.com/photos/library/guides/get-started-java

val code = "4/0AY0e-g46e7N0D-zZumm-L6gBVaS_AIOmzd6jE3_L9JyQMP11S5bZ_zOduHTqWR-UPih6xQ"
val clientId = "364191103716-pqmellfm9rd28m068apih4rr5vmrakeb.apps.googleusercontent.com"
val secret = "L2ZHj0KW10tcAjyXNoiisfdG"


val userAuthorizer = UserAuthorizer.newBuilder()
    .setClientId(ClientId.of(clientId, secret))
    .setScopes(setOf(
        "https://www.googleapis.com/auth/photoslibrary.readonly",
        "https://www.googleapis.com/auth/photoslibrary.appendonly",
        "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata",
        "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata",
    ))
    .setCallbackUri(URI.create("http://localhost"))
    .setTokenStore(MemoryTokensStorage())
    .build()


@SpringBootApplication
class GPhoto

fun main(args : Array<String>) {

    System.setOut(PrintStream(FileOutputStream("gphoto.log"), true))

    val log = LoggerFactory.getLogger(GPhoto::class.java)
    //getCode()
    /*
    val cred = userAuthorizer.getCredentialsFromCode(code, URI("http://localhost"))
    println("ACCESS: ${cred.accessToken}")
    println("REFRESH: ${cred.refreshToken}")

     */
    val tok = AccessToken("ya29.a0AfH6SMCzSH27VPNv3EAVNFhUaYBssl6tvQo4hp1wwq5JLhz4Hnwl8kEWnl6bLGU7mbl1X80hA8XKFp8DQdj93hA8SK6sGryA0Pvn4wl_kwad8PBWoa25Dw0hB0ou9f0jUNUe_1mKy5kio0q-ZWYQfsWrbEtm", Date(1615890052769L))
    val refreshToken = "1//0982YjVtv6znrCgYIARAAGAkSNwF-L9IrikFYT39zYTh_B0mI_ccgBsHYMeklVNOVuFgN_ijo3_Ng2rsY4HTk7Jj7QNb8Kpj4QdA"

    PhotosLibraryClient.initialize(settings(tok, refreshToken)).use { client ->

        client.listAlbums().iterateAll().asSequence()
            .map { album ->
                log.info("Next Albumb ID: ${album.id} TITLE: ${album.title}  SIZE: ${album.mediaItemsCount}")
                album to client.searchMediaItems(album.id).iterateAll()
                    .asSequence()
                    .map { it.filename }
                    .toList()
            }
            .map { (album, uploadedFiles) ->
                Triple(album, uploadedFiles, findMatchingDirectory(File(basePath), album.title.split("_"))?.let { dir: File ->
                    println("Found matching dir ${dir.canonicalPath}")
                    dir.listFiles()
                        .filter { uploadedFiles.contains(it.name).not() }
                        .filter { it.isFile }
                        .filter {  it.name.toLowerCase().endsWith("jpeg") || it.name.toLowerCase().endsWith("jpg")}
                        .sortedBy { f -> f.name }
                })
        }.map { (album, uploadedFiles, toUpload) ->
            toUpload?.batchesOf(20)?.toList()?.map { files ->
                files.map { file ->
                    file to RandomAccessFile(file, "r").use { raf ->
                        UploadMediaItemRequest.newBuilder()
                            // The media type (e.g. "image/png")
                            .setMimeType(
                                when {
                                    file.name.toLowerCase().endsWith("jpeg") -> "image/jpeg"
                                    file.name.toLowerCase().endsWith("jpg") -> "image/jpeg"
                                    else -> throw IllegalStateException("Unknown mime type for ${file.name}")
                                }
                            )
                            // The file to upload
                            .setDataFile(raf)
                            .build()
                            .let { request ->
                                println("Uploading media item ${file.name}")
                                client.uploadMediaItem(request)
                            }
                    }.let { response ->
                        if (response.error.isPresent)
                            throw IllegalStateException(response.error.get().toString())
                        response.uploadToken.get()
                    }
                }?.map { (file, uploadToken) ->
                    println("preparing  media item ${file.name} with  upload token $uploadToken")
                    NewMediaItemFactory.createNewMediaItem(uploadToken, file.name, file.name)
                }?.let { mediaItems ->
                    if (mediaItems.isNotEmpty()) {
                        println("uploading batch of  ${mediaItems.size} media items to album ${album.id} with name ${album.title}")
                        client.batchCreateMediaItems(album.id, mediaItems).newMediaItemResultsList.forEach { itemResponse ->
                            if (itemResponse.status.code == Code.OK_VALUE)
                                println("Successfully uploaded ${itemResponse.mediaItem.filename} to album ${album.id} with name ${album.title}")
                        }
                    }
                }
            }
            Thread.sleep(1000)
        }.forEach { println("go") }
    }

        /*
        client.listMediaItems().iterateAll()
            .asSequence()
            .map {
                1.apply {
                    println("---------------------------------------------------")
                    println("::${it.filename}  => ${it.mimeType}")
                    println(":: DESCRIPTION: ${it.description}")
                    println(" META DATA -----------------")
                    it.mediaMetadata.allFields.forEach { d, v -> println("${d.type} : $v")}
                    println(" ALL FIELDS -----------------")
                    it.allFields.forEach { d, v -> println("${d.type} : $v")}
                }
            }
            .sum().let { println("TOTAL $it") }
*/
        /*
        client.listAlbums().iterateAll()
            .asSequence()
            .filter { it.mediaItemsCount == 0L }
            .forEach {
                    println("${it.id}: ${it.title} - SIZE = ${it.mediaItemsCount}")
            }
            */



}

val basePath = "/media/exssinclair/d52e7412-daa6-4c27-b67b-dd75f1470cff/pending"
fun findMatchingDirectory(baseDir : File, parts : List<String>) : File? {
    return parts
        .possibleCombos()
        .mapNotNull { (name, rest) ->
//            println("COMBO '$name + $rest'")
            File(baseDir, name).let { file ->
 //               println("${file.canonicalPath} exists: ${file.exists()}")
                when {
                    file.exists() -> when (rest.isEmpty()) {
                        true -> file
                        else -> findMatchingDirectory(file, rest)
                    }
                    else -> file
                }
            }
        }
        .find { it.exists() }
}

fun <T> Iterable<T>.batchesOf(num : Int) : Sequence<List<T>> {
    var list = mutableListOf<T>()
    val i = iterator()
    return generateSequence {
        while (i.hasNext() && list.size < num) {
            list.add(i.next())
        }
        list.toList().let { result ->
            if (result.isEmpty()) null
            else result.also { list.clear() }
        }
    }
}

fun List<String>.possibleCombos() : Sequence<Pair<String,List<String>>> {
    val i = iterator().withIndex()
    var acc = mutableListOf<String>()
    return generateSequence{
        when (i.hasNext()) {
            false -> null
            else -> i.next().let { next ->
                acc.add(next.value)
                acc.joinToString("_") to subList(next.index+1, size)
            }
        }
    }
}

fun settings(accessToken : AccessToken, refreshToken : String) = PhotosLibrarySettings.newBuilder().apply {
    credentialsProvider = CredentialsProvider {
        UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(secret)
            .setRefreshToken(refreshToken)
            .setAccessToken(accessToken)
            .setTokenServerUri(null)
            .setQuotaProjectId(quotaProjectId)
            .build()
    }
}.build()


fun getCode() {
    println("Paste in browser: ${userAuthorizer.getAuthorizationUrl(null, null, null)}")
    println("ENTER CODE:")
    val authorizationCode =  BufferedReader(InputStreamReader(System.`in`)).readLine()
    println("AUTH: $authorizationCode")
    /*
    println("ACCESS: ${userAuthorizer.getCredentials(code).accessToken}")
    println("REFRESH: ${userAuthorizer.getCredentials(code).refreshToken}")

     */
}
/*
curl \
  -d code=4/0AY0e-g6lVRpzJx4NrCScolg7Ged7JBKc2cMTtuVi0JN9yrQfFV0F04EHZlEzPSNrxSa16Q \
  -d client_id=364191103716-pqmellfm9rd28m068apih4rr5vmrakeb.apps.googleusercontent.com \
  -d client_secret=L2ZHj0KW10tcAjyXNoiisfdG \
  -d redirect_uri=http://localhost \
  -d grant_type=authorization_code https://www.googleapis.com/oauth2/v4/token
{
  "access_token": "ya29.a0AfH6SMDeSGGtCSkQkTvTAhzUTlJJtdLKRNxPjPs13zDapIZQdKRiqOxCA3lQeZyPTbnTH32ndwGQtN5uAKTLNgF8hoIihlPQlwec0_GZbcAO0BjR1oLJPzc7AuPcJEuxUO_jv_G3W4giL2lhEPbU8NYRYjCc",
  "expires_in": 3599,
  "refresh_token": "1//09UYGJbAs89oiCgYIARAAGAkSNwF-L9IrGGDXfHeGeXsuWcISIMYlk_EGRluVizG3ibJYeVVlQ6RGP3aBbwxogb2hO6EBVaYiOyA",
  "scope": "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/photoslibrary.appendonly https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata",
  "token_type": "Bearer"
}%
 */