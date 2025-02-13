package com.kpstv.yts.ui.fragments.sheets

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.core.view.isVisible
import com.kpstv.yts.AppInterface
import com.kpstv.yts.R
import com.kpstv.yts.data.models.response.Model
import com.kpstv.yts.databinding.BottomSheetLibraryDownloadBinding
import com.kpstv.yts.databinding.ButtonCastPlayBinding
import com.kpstv.yts.databinding.ButtonLocalPlayBinding
import com.kpstv.yts.databinding.CustomProgressBinding
import com.kpstv.yts.extensions.views.ExtendedBottomSheetDialogFragment
import com.kpstv.common_moviesy.extensions.viewBinding
import com.kpstv.yts.ui.activities.TorrentPlayerActivity
import com.kpstv.yts.ui.helpers.PremiumHelper
import com.kpstv.yts.ui.helpers.SubtitleHelper
import com.kpstv.common_moviesy.extensions.hide
import com.kpstv.navigation.BaseArgs
import com.kpstv.navigation.getKeyArgs
import es.dmoral.toasty.Toasty
import kotlinx.android.parcel.Parcelize
import java.io.File
import java.io.Serializable

enum class PlaybackType : Serializable {
    LOCAL,
    REMOTE
}

class BottomSheetLibraryDownload : ExtendedBottomSheetDialogFragment(R.layout.bottom_sheet_library_download) {

    interface Callbacks {
        fun loadCastMedia(model: Model.response_download, platFromLast: Boolean, srtFile: File?, loadComplete: (Exception?) -> Unit?)
    }

    private val binding by viewBinding(BottomSheetLibraryDownloadBinding::bind)

    private lateinit var model: Model.response_download
    private lateinit var subtitleHelper: SubtitleHelper

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = getKeyArgs<Args>()
        /** Get the model from the arguments */
        model = args.model

        /** Show the last played position */
        if (model.lastSavedPosition != 0) {
            binding.checkBoxPlayFrom.text =
                "Play from last save position (${model.lastSavedPosition / (1000 * 60)} mins)"
        } else binding.checkBoxPlayFrom.hide()

        /** Set view according to playback type */
        when (args.playbackType) {
            PlaybackType.LOCAL -> { // Local mode

                ButtonLocalPlayBinding.inflate(layoutInflater, binding.root, true).apply {
                    playButton.setOnClickListener { localPlayButtonClicked() }
                }

                /** Show subtitle view */
                showSubtitle()
            }
            PlaybackType.REMOTE -> { // Cast mode

                ButtonCastPlayBinding.inflate(layoutInflater, binding.root, true).apply {
                    castButton.setOnClickListener { remotePlayButtonClicked() }
                }

                /** Show subtitle only if the premium is unlocked */
                if (AppInterface.IS_PREMIUM_UNLOCKED) {
                    showSubtitle()
                } else {
                    PremiumHelper.insertSubtitlePremiumTip(requireContext(), parentFragmentManager, binding.addLayout) { dismiss() }
                }
            }
        }
    }

    private fun remotePlayButtonClicked() {
        /** Find a subtitle track if exist */
        val subtitleFile = if (::subtitleHelper.isInitialized)
            subtitleHelper.getSelectedSubtitle() else null

        if (model.videoPath != null) {
            /** This must be called before clearing bottom sheet */
            val playFromLastPosition =
                binding.checkBoxPlayFrom.isVisible && binding.checkBoxPlayFrom.isChecked

            /** Clear the bottom sheet view */
            binding.root.removeAllViews()
            CustomProgressBinding.inflate(layoutInflater, binding.root, true)

            if (context is Callbacks) {
                (context as Callbacks).loadCastMedia(
                    model = model,
                    platFromLast = playFromLastPosition,
                    srtFile = subtitleFile,
                    loadComplete = { error ->
                        if (error != null) {
                            Toasty.error(
                                requireContext(),
                                error.message ?: requireContext().getString(
                                    R.string.error_unknown
                                )
                            ).show()
                        }
                        dismiss()
                    }
                )
            }
        } else
            Toasty.error(
                requireContext(),
                requireContext().getString(R.string.error_video_path)
            ).show()
    }

    private fun localPlayButtonClicked() {
        val i = Intent(context, TorrentPlayerActivity::class.java)
        i.putExtra(TorrentPlayerActivity.ARG_NORMAL_LINK, model.videoPath)
        i.putExtra(TorrentPlayerActivity.ARG_TORRENT_HASH, model.hash)
        i.putExtra(TorrentPlayerActivity.ARG_MOVIE_ID, model.movieId)
        i.putExtra(TorrentPlayerActivity.ARG_MOVIE_TITLE, model.title)

        if (binding.checkBoxPlayFrom.isVisible && binding.checkBoxPlayFrom.isChecked) {
            i.putExtra(TorrentPlayerActivity.ARG_LAST_SAVE_POS, model.lastSavedPosition)
        }

        if (::subtitleHelper.isInitialized) {
            i.putExtra(TorrentPlayerActivity.ARG_SUBTITLE_NAME, subtitleHelper.getSelectedSubtitle()?.name)
        }

        startActivity(i)
        dismiss()
    }

    private fun showSubtitle() {
        subtitleHelper = SubtitleHelper.Builder(requireContext(), parentFragmentManager)
            .setTitle(model.title)
            .setImdbCode(model.imdbCode!!)
            .setParentView(binding.root)
            .setAddLayout(binding.addLayout)
            .setParentBottomSheet(this)
            .build().apply {
                populateSubtitleView()
            }
    }

    @Parcelize
    data class Args(val playbackType: PlaybackType, val model: Model.response_download): BaseArgs(), Parcelable
}