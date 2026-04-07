package dev.filips.twistcounter.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.filips.twistcounter.R
import dev.filips.twistcounter.domain.model.Ride
import java.time.format.DateTimeFormatter

class RideHistoryAdapter(
    private val onRideClick: (Ride) -> Unit
) : ListAdapter<Ride, RideHistoryAdapter.RideViewHolder>(RideDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_history, parent, false)
        return RideViewHolder(view, onRideClick)
    }

    override fun onBindViewHolder(holder: RideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RideViewHolder(
        itemView: View,
        private val onRideClick: (Ride) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val dateText: TextView = itemView.findViewById(R.id.rideDate)
        private val distanceText: TextView = itemView.findViewById(R.id.rideDistance)
        private val cornersText: TextView = itemView.findViewById(R.id.rideCorners)
        private val leanText: TextView = itemView.findViewById(R.id.rideLean)

        private var currentRide: Ride? = null

        init {
            itemView.setOnClickListener {
                currentRide?.let { onRideClick(it) }
            }
        }

        fun bind(ride: Ride) {
            currentRide = ride

            val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm")
            val localDateTime = java.time.LocalDateTime.ofInstant(ride.startTime, java.time.ZoneId.systemDefault())
            dateText.text = localDateTime.format(formatter)

            distanceText.text = String.format("%.1f km", ride.distanceKm)
            cornersText.text = "${ride.cornerCount} corners"

            val maxLean = maxOf(ride.maxLeanLeft, ride.maxLeanRight)
            leanText.text = "${maxLean.toInt()}° max lean"
        }
    }

    class RideDiffCallback : DiffUtil.ItemCallback<Ride>() {
        override fun areItemsTheSame(oldItem: Ride, newItem: Ride): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Ride, newItem: Ride): Boolean {
            return oldItem == newItem
        }
    }
}
