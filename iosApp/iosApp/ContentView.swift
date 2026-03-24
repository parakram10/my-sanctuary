import ComposeApp
import SwiftUI

private struct ComposeRootView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosRootKt.createRootViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeRootView()
            .ignoresSafeArea()
    }
}

#Preview {
    ContentView()
}
