//
//  ConferenceCaseRow.swift
//  CardiacSurgeryCopilot
//

import SwiftUI

/// One recent conference case, styled as a tappable card. Status uses
/// neutral tones (not red) since "In Progress" is a routine, expected
/// state here -- not a warning.
struct ConferenceCaseRow: View {
    let record: ConferenceCaseSummary

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
        case .collectingInformation: "In Progress"
        case .readyToFinalize: "Ready"
        case .completed: "Completed"
        }
    }

    // Deliberately neutral (grays + a touch of teal) -- these are routine
    // workflow states, not alerts, so nothing here reads as red/urgent.
    private var statusTint: Color {
        switch record.status {
        case .collectingInformation: Color(.systemGray5)
        case .readyToFinalize: Color.copilotPrimaryText.opacity(0.16)
        case .completed: Color(.systemGray5)
        }
    }

    private var statusTextColor: Color {
        switch record.status {
        case .collectingInformation: Color.slateText
        case .readyToFinalize: Color.copilotPrimaryText
        case .completed: Color.slateText
        }
    }
}

#Preview {
    VStack(spacing: 10) {
        ConferenceCaseRow(record: ConferenceCaseSummary(id: "1", title: "68-year-old man, severe aortic stenosis, planned SAVR", createdAt: Date(), status: .collectingInformation))
        ConferenceCaseRow(record: ConferenceCaseSummary(id: "2", title: "54-year-old woman, three-vessel CAD, CABG vs. PCI", createdAt: Date(), status: .readyToFinalize))
        ConferenceCaseRow(record: ConferenceCaseSummary(id: "3", title: "72-year-old man, mitral regurgitation, planned repair", createdAt: Date(), status: .completed))
    }
    .padding()
    .background(Color.warmBackground)
}
