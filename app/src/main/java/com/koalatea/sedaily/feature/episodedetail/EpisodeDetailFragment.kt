package com.koalatea.sedaily.feature.episodedetail

import android.Manifest
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.google.android.material.snackbar.Snackbar
import com.koalatea.sedaily.R
import com.koalatea.sedaily.database.table.Episode
import com.koalatea.sedaily.feature.downloader.DownloadStatus
import com.koalatea.sedaily.network.Resource
import com.koalatea.sedaily.util.supportActionBar
import com.nabinbhandari.android.permissions.PermissionHandler
import com.nabinbhandari.android.permissions.Permissions
import kotlinx.android.synthetic.main.fragment_episode_detail.*
import org.koin.androidx.viewmodel.ext.android.viewModel

class EpisodeDetailFragment : Fragment() {

    private val viewModel: EpisodeDetailViewModel by viewModel()

    lateinit var episodeId: String

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.fragment_episode_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val safeArgs: EpisodeDetailFragmentArgs by navArgs()
        episodeId = safeArgs.episodeId

        supportActionBar?.elevation = resources.getDimension(R.dimen.toolbar_elevation)

        downloadButton.setOnClickListener {
            Permissions.check(context, Manifest.permission.WRITE_EXTERNAL_STORAGE, getString(R.string.rationale_storage_permission), object : PermissionHandler() {
                override fun onGranted() {
                    downloadButton.isEnabled = false

                    viewModel.download()
                }
            })
        }

        deleteButton.setOnClickListener {
            promptDeleteDownload { viewModel.delete() }
        }

        viewModel.episodeDetailsResource.observe(this, Observer { resource ->
            when (resource) {
                is Resource.Loading -> showLoading()
                is Resource.Success<Episode> -> renderEpisode(resource.data)
                is Resource.Error -> acknowledgeGenericError()
            }
        })

        viewModel.downloadStatusLiveData.observe(this, Observer {
            val downloadStatus = it.peekContent()
            when(downloadStatus) {
                is DownloadStatus.Initial -> showDownloadViews()
                is DownloadStatus.Unknown -> showDownloadViews()
                is DownloadStatus.Downloading -> showDownloadProgress(downloadStatus.progress)
                is DownloadStatus.Downloaded -> showDeleteViews()
                is DownloadStatus.Error -> showDownloadViews()
            }

            // FIXME :: Do not show unless triggered by user
            it.getContentIfNotHandled()?.let {
                when(downloadStatus) {
                    is DownloadStatus.Initial -> showDownloadViews()
                    is DownloadStatus.Unknown -> acknowledgeDownloadFailed()
                    is DownloadStatus.Downloading -> showDownloadProgress(downloadStatus.progress)
                    is DownloadStatus.Downloaded -> acknowledgeDownloadSucceeded()
                    is DownloadStatus.Error -> acknowledgeDownloadFailed()
                }
            }
        })

        viewModel.fetchEpisodeDetails(episodeId)
    }

    private fun showDownloadProgress(progress: Float) {
        downloadProgressBar.progress = progress.toInt()
        downloadProgressBar.visibility = View.VISIBLE
    }

    private fun showLoading() {
        headerCardView.visibility = View.GONE
        detailsCardView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
    }

    private fun renderEpisode(episode: Episode) {
        supportActionBar?.title = episode.titleString

        episodeTitleTextView.text = episode.titleString

        context?.let { context ->
            Glide.with(context)
                    .load(episode.httpsGuestImageUrl)
                    .transform(MultiTransformation(CenterCrop(), CircleCrop()))
                    .placeholder(R.drawable.vd_image)
                    .error(R.drawable.vd_broken_image)
                    .into(guestImageView)
        }

        dateTextView.text = DateFormat.getDateFormat(context).format(episode.utcDate)

        var html = episode.content?.rendered!!
        html = removePowerPressPlayerTags(html)
        html = addStyling(html)
        html = addScaleMeta(html)
        contentWebView.loadData(html,  "text/html", null)

        // Hide loading view and show content.
        progressBar.visibility = View.GONE
        headerCardView.visibility = View.VISIBLE
        detailsCardView.visibility = View.VISIBLE
    }

    private fun removePowerPressPlayerTags(html: String): String {
        var modifiedHtml = html

        modifiedHtml = modifiedHtml.replaceFirst("<!--powerpress_player-->", "")

        /////////////////////////
        modifiedHtml = modifiedHtml.replaceFirst( "<div class=\"powerpress_player\".*</div>", "")

        /////////////////////////
        modifiedHtml = modifiedHtml.replaceFirst( "<p class=\"powerpress_links powerpress_links_mp3\">.*</p>", "")

        return modifiedHtml
    }

    private fun addStyling(html: String) = "<style type=\"text/css\">body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; } </style>$html"

    private fun addScaleMeta(html: String) = "<meta name=\"viewport\" content=\"initial-scale=1.0\" />$html"

    private fun showDownloadViews() {
        deleteButton.visibility = View.GONE
        downloadButton.isEnabled = true
        downloadButton.visibility = View.VISIBLE
        downloadProgressBar.visibility = View.INVISIBLE
    }

    private fun showDeleteViews() {
        deleteButton.visibility = View.VISIBLE
        downloadButton.visibility = View.INVISIBLE
        downloadProgressBar.visibility = View.INVISIBLE
    }

    private fun acknowledgeGenericError() = Snackbar.make(containerConstraintLayout, R.string.error_generic, Snackbar.LENGTH_SHORT).show()

    private fun acknowledgeDownloadSucceeded() = Snackbar.make(containerConstraintLayout, R.string.episode_download_succeeded, Snackbar.LENGTH_SHORT).show()
    private fun acknowledgeDownloadFailed() = Snackbar.make(containerConstraintLayout, R.string.episode_download_failed, Snackbar.LENGTH_SHORT).show()
    private fun promptDeleteDownload(positiveCallback: () -> Unit) {
        AlertDialog.Builder(requireContext())
                .setMessage(R.string.episode_delete_download_prompt)
                .setPositiveButton(R.string.yes) { _, _ -> positiveCallback() }
                .setNegativeButton(R.string.no, null)
                .show()
    }

}