import SwiftUI
import BasilUI

// Platform entry point only. App UI, networking, and behavior are Kotlin.
@main
struct BasilHost: App {
    var body: some Scene { WindowGroup { KotlinView().ignoresSafeArea() } }
}

struct KotlinView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController { MainViewControllerKt.MainViewController() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
