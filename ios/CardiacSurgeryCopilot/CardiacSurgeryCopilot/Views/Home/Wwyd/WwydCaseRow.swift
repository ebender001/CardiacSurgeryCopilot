//
//  WwydCaseRow.swift
//  CardiacSurgeryCopilot
//

import SwiftUI

/// One recent WWYD discussion, styled as a tappable card.
struct WwydCaseRow: View {
    let record: WwydCaseSummary

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(record.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Text(record.createdAt, style: .date)
                    .font(.caption)
                    .foregroundStyle(Color.slateText)
            }

            Spacer(minLength: 8)

            statusBadge
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(Color.primary.opacity(0.06), lineWidth: 1)
        )
    }

    private var statusBadge: some View {
        StatusBadge(text: statusText, tint: statusTint, textColor: statusTextColor)
    }

    private var statusText: String {
        switch record.status {
        case .active: "Active"
        case .archived: "Archived"
        }
    }

    private var statusTint: Color {
        switch record.status {
        case .active: Color.copilotPrimaryText.opacity(0.16)
        case .archived: Color(.systemGray5)
        }
    }

    private var statusTextColor: Color {
        switch record.status {
        case .active: Color.copilotPrimaryText
        case .archived: Color.slateText
        }
    }
}

#Preview {
    VStack(spacing: 10) {
        WwydCaseRow(record: WwydCaseSummary(id: "1", title: "58-year-old man, acute type A dissection", createdAt: Date(), status: .active))
        WwydCaseRow(record: WwydCaseSummary(id: "2", title: "66-year-old woman, infective endocarditis", createdAt: Date(), status: .archived))
    }
    .padding()
    .background(Color.warmBackground)
}
