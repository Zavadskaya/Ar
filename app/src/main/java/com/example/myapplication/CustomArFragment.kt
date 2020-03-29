package com.example.myapplication


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.myapplication.FirebaseUtils.storageRef
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ExternalTexture
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException


class CustomArFragment : ArFragment() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var externalTexture: ExternalTexture
    private lateinit var videoRenderable: ModelRenderable
    private lateinit var videoAnchorNode: AnchorNode

    private var activeAugmentedImage: AugmentedImage? = null
    lateinit var image: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaPlayer = MediaPlayer()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        planeDiscoveryController.hide()
        planeDiscoveryController.setInstructionView(null)
        arSceneView.planeRenderer.isEnabled = false
        arSceneView.isLightEstimationEnabled = false
        initializeSession()
        createArScene()

        loadImage()
        loadVideo()
        return view
    }

    fun loadImage() {
        val pathReference: StorageReference =
            storageRef.reference.child("images").child("test_image_2.jpg")
        val localFile = File(context?.cacheDir, "images.jpg")

        pathReference.getFile(localFile)
            .addOnSuccessListener {
            }.addOnFailureListener { TODO("Not yet implemented") }
    }

    fun loadVideo() {
        val pathReference: StorageReference =
            storageRef.reference.child("videos").child("test_video_2.mp4")
        val localFile = File(context?.cacheDir, "test_video_2.mp4")

        pathReference.getFile(localFile)
            .addOnSuccessListener {
            }.addOnFailureListener { TODO("Not yet implemented") }
    }

    override fun getSessionConfiguration(session: Session): Config {

        fun loadAugmentedImageBitmap(imageName: String): Bitmap =
            requireContext().assets.open(imageName).use { return BitmapFactory.decodeStream(it) }

        fun loadFromCache(fileName: String): Bitmap =
            FileInputStream(
                File(
                    context?.cacheDir,
                    fileName
                )
            ).use { return BitmapFactory.decodeStream(it) as Bitmap }

        fun setupAugmentedImageDatabase(config: Config, session: Session): Boolean {
            try {
                config.augmentedImageDatabase = AugmentedImageDatabase(session).also { db ->
                    db.addImage(TEST_VIDEO_2, loadFromCache("images.jpg"))
                    db.addImage(TEST_VIDEO_5, loadAugmentedImageBitmap(TEST_IMAGE_5))

                }
                return true
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).e(e)
            } catch (e: IOException) {
                Timber.tag(TAG).e(e)
            }
            return false
        }

        return super.getSessionConfiguration(session).also {
            it.lightEstimationMode = Config.LightEstimationMode.DISABLED
            it.focusMode = Config.FocusMode.AUTO

            if (!setupAugmentedImageDatabase(it, session)) {
                Toast.makeText(
                    requireContext(),
                    "Could not setup augmented image database",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createArScene() {
        externalTexture = ExternalTexture().also {
            mediaPlayer.setSurface(it.surface)
        }


        ModelRenderable.builder()
            .setSource(requireContext(), R.raw.augmented_video_model)
            .build()
            .thenAccept { renderable ->
                videoRenderable = renderable
                renderable.isShadowCaster = false
                renderable.isShadowReceiver = false
                renderable.material.setExternalTexture("videoTexture", externalTexture)
            }
            .exceptionally { throwable ->
                Timber.tag(TAG).e(throwable)
                return@exceptionally null
            }

        videoAnchorNode = AnchorNode().apply {
            setParent(arSceneView.scene)
        }
    }

    override fun onUpdate(frameTime: FrameTime) {
        val frame = arSceneView.arFrame ?: return

        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        val nonFullTrackingImages =
            updatedAugmentedImages.filter { it.trackingMethod != AugmentedImage.TrackingMethod.FULL_TRACKING }
        activeAugmentedImage?.let { activeAugmentedImage ->
            if (isArVideoPlaying() && nonFullTrackingImages.any { it.index == activeAugmentedImage.index }) {
                pauseArVideo()
            }
        }

        val fullTrackingImages =
            updatedAugmentedImages.filter { it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING }
        if (fullTrackingImages.isEmpty()) return

        activeAugmentedImage?.let { activeAugmentedImage ->
            if (fullTrackingImages.any { it.index == activeAugmentedImage.index }) {
                if (!isArVideoPlaying()) {
                    resumeArVideo()
                }
                return
            }
        }

        fullTrackingImages.firstOrNull()?.let { augmentedImage ->
            try {
                playbackArVideo(augmentedImage)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e)
            }
        }
    }

    private fun isArVideoPlaying() = mediaPlayer.isPlaying

    private fun pauseArVideo() {
        videoAnchorNode.renderable = null
        mediaPlayer.pause()
    }

    private fun resumeArVideo() {
        mediaPlayer.start()
        videoAnchorNode.renderable = videoRenderable
    }

    private fun dismissArVideo() {
        videoAnchorNode.anchor?.detach()
        videoAnchorNode.renderable = null
        activeAugmentedImage = null
        mediaPlayer.reset()
    }

    fun loadFromCacheVideo(fileName: String) =
        FileInputStream(File(context?.cacheDir, fileName)).fd.also { descriptor ->
            mediaPlayer.reset()
            mediaPlayer.setDataSource(descriptor)
        }.also {
            mediaPlayer.isLooping = true
            mediaPlayer.prepare()
            mediaPlayer.start()
        }

    private fun playbackArVideo(augmentedImage: AugmentedImage) {
        Timber.tag(TAG).d("playbackVideo = ${augmentedImage.name}")


        loadFromCacheVideo(augmentedImage.name)
//        requireContext().assets.openFd(augmentedImage.name)
//            .use { descriptor ->
//                mediaPlayer.reset()
//                mediaPlayer.setDataSource(descriptor)
//            }.also {
//                mediaPlayer.isLooping = true
//                mediaPlayer.prepare()
//                mediaPlayer.start()
//            }


        videoAnchorNode.anchor?.detach()
        videoAnchorNode.anchor = augmentedImage.createAnchor(augmentedImage.centerPose)
        videoAnchorNode.localScale = Vector3(augmentedImage.extentX, 1.0f, augmentedImage.extentZ)

        activeAugmentedImage = augmentedImage

        externalTexture.surfaceTexture.setOnFrameAvailableListener {
            it.setOnFrameAvailableListener(null)
            videoAnchorNode.renderable = videoRenderable
        }
    }

    override fun onPause() {
        super.onPause()
        dismissArVideo()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release()
    }

    companion object {
        private var TAG = CustomArFragment::class.java.simpleName

        private const val TEST_IMAGE_2 = "test_image_2.jpg"
        private const val TEST_IMAGE_3 = "test_image_3.jpg"
        private const val TEST_IMAGE_4 = "test_image_4.jpg"
        private const val TEST_IMAGE_5 = "test_image_5.jpg"

        private const val TEST_VIDEO_2 = "test_video_2.mp4"
        private const val TEST_VIDEO_3 = "test_video_3.mp4"
        private const val TEST_VIDEO_5 = "test_video_5.mp4"

    }
}