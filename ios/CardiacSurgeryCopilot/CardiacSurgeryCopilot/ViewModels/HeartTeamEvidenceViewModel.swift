//
//  HeartTeamEvidenceViewModel.swift
//  CardiacSurgeryCopilot
//
//  Drives HeartTeamEvidenceView: loads one role's pro/con PubMed evidence
//  (cached server-side, so a no-op if already loaded).
//

import Combine
import Foundation

@MainActor
final class HeartTeamEvidenceViewModel: ObservableObject {
    @Published private(set) var evidence: HeartTeamRoleEvidence?
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    let caseId: String
    let role: HeartTeamRole

    init(caseId: String, role: HeartTeamRole) {
        self.caseId = caseId
        self.role = role
    }

    #if DEBUG
    convenience init(caseId: String, role: HeartTeamRole, previewEvidence: HeartTeamRoleEvidence) {
        self.init(caseId: caseId, role: role)
        evidence = previewEvidence
    }
    #endif

    func load() async {
        guard evidence == nil else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            evidence = try await BackendService.getHeartTeamRoleEvidence(caseId: caseId, role: role)
            errorMessage = nil
        } catch {
            errorMessage = (error as? LocalizedError)?.errorDescription ?? "Couldn't load evidence. Please try again."
        }
    }
}
