import type { Metadata } from 'next'
import { Assistant } from 'next/font/google'
import './globals.css'
import { FeedbackChat } from '@automate/feedback-lib/FeedbackChat'

// Assistant is a Hebrew face built for signage-like clarity at small sizes.
const assistant = Assistant({
  subsets: ['hebrew', 'latin'],
  weight: ['400', '600', '700', '800'],
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'שלט חום',
  description:
    'כל היעדים שהשילוט החום בישראל מפנה אליהם — גנים לאומיים, שמורות טבע, מוזיאונים ואתרי מורשת — לפי הקרוב אליך.',
}

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="he" dir="rtl" className={assistant.className}>
      <body className="min-h-dvh antialiased">
        {children}
        <FeedbackChat issuesPath="/feedback-lib-issues" />
      </body>
    </html>
  )
}
