//
//  ListErrorState.swift
//  CardiacSurgeryCopilot
//
//  Shown in place of a recent-items list when loading it from the backend
//  fails with nothing previously loaded to fall back to (see
//  ConferenceHomeViewModel.refresh() -- a background refresh failing
//  while a list is already on screen just keeps that stale list instead
//  of showing this).
//

import SwiftUI

struct ListErrorState: View {
    let title: String
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)

            Text(message)
                .font(.footnote)
                .foregroundStyle(Color.slateText)
                .fixedSize(horizontal: false, vertical: true)

            Button("Try Again", action: onRetry)
                .font(.subheadline.weight(.semibold))
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }
}

#Preview {
    ListErrorState(
        title: "Couldn't load Recent Cases",
        message: "Heart Team Prep couldn't reach the server. Check your connection and try again.",
        onRetry: {}
    )
    .padding()
    .background(Color.warmBackground)
}
