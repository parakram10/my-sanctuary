# Environment Variables Setup Guide

This guide explains how to configure API keys for the Sanctuary app.

## Quick Start

### 1. Create .env file

```bash
cp .env.example .env
```

### 2. Get API Keys

**Option A: Groq (Recommended for Testing - FREE)**
- Go to: https://console.groq.com/keys
- Create free account
- Copy your API key
- Add to `.env`: `GROQ_API_KEY=gsk_...`

**Option B: Claude (Premium Quality - PAID)**
- Go to: https://console.anthropic.com/account/keys
- Sign in with Anthropic account
- Copy your API key
- Add to `.env`: `CLAUDE_API_KEY=sk_ant_...`

### 3. Load Environment Variables

#### macOS/Linux
```bash
source setup-env.sh
./gradlew :composeApp:installDebug
```

#### Windows
```cmd
setup-env.bat
gradlew :composeApp:installDebug
```

#### Xcode (iOS Development)
1. Open `iosApp/iosApp.xcodeproj`
2. Product → Scheme → Edit Scheme
3. Run → Pre-actions
4. Add environment variables:
   - `GROQ_API_KEY = gsk_...`
   - `CLAUDE_API_KEY = sk_ant_...` (optional)
5. Click Close and run

## .env File Format

```env
# Required - at least one must be set
GROQ_API_KEY=gsk_YOUR_KEY_HERE
CLAUDE_API_KEY=sk_ant_YOUR_KEY_HERE

# Optional - switch between providers
AI_PROVIDER=groq
```

## API Provider Comparison

| Provider | Cost | Speed | Quality | Use Case |
|----------|------|-------|---------|----------|
| **Groq** | FREE (5000 req/day) | Fast (90+ tok/s) | Good | Development, Testing |
| **Claude** | ~$0.003/request | Slower | Excellent | Production, Premium Users |

## Switching Providers

### Quick Switch

1. Change `AI_PROVIDER` in `.env`:
   ```env
   AI_PROVIDER=groq    # Use Groq (free)
   # AI_PROVIDER=claude # Use Claude (premium)
   ```

2. Reload environment and rebuild:
   ```bash
   source setup-env.sh
   ./gradlew clean :composeApp:installDebug
   ```

### Programmatic Switch

Or change in `InsightModule.kt`:
```kotlin
internal const val AI_PROVIDER = "groq"  // Change to "claude"
```

## Security Best Practices

### ✅ DO
- Keep `.env` in `.gitignore` (already configured)
- Use environment variables for local development
- Rotate API keys periodically
- Use separate keys for dev/test/production
- Store production keys in secure CI/CD secrets

### ❌ DON'T
- Commit `.env` to git
- Share API keys in chat/email
- Hardcode keys in source code
- Use same key for multiple environments
- Log or print API keys

## Troubleshooting

### "API key not configured"
```
Check that:
1. .env file exists: ls .env
2. .env is sourced: source setup-env.sh
3. Key is not empty: cat .env | grep GROQ_API_KEY
4. Key format is correct: gsk_... for Groq, sk_ant_... for Claude
```

### "Invalid API key"
```
Check that:
1. Key hasn't expired or been revoked in console
2. Key is not truncated or contains extra whitespace
3. You have the right provider selected in AI_PROVIDER
```

### Xcode can't find API key
```
1. Open scheme settings: Product → Scheme → Edit Scheme
2. Verify environment variable is set in Run → Pre-actions
3. Restart Xcode
4. Rebuild the project
```

## File Structure

```
Sanctuary/
├── .env                  # Local API keys (gitignored)
├── .env.example          # Template with all keys needed
├── setup-env.sh          # macOS/Linux environment setup
├── setup-env.bat         # Windows environment setup
├── ENV_SETUP.md          # This file
└── .gitignore            # Includes .env
```

## Multiple Developers

Each developer should:

1. Clone the repo (no `.env` committed)
2. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```
3. Add their own API key:
   ```bash
   # Get key from Groq or Claude
   # Edit .env with your key
   ```
4. Source before building:
   ```bash
   source setup-env.sh
   ./gradlew :composeApp:installDebug
   ```

## CI/CD Integration

For GitHub Actions, GitLab CI, or other CI/CD:

```yaml
# Example GitHub Actions
env:
  GROQ_API_KEY: ${{ secrets.GROQ_API_KEY }}
  CLAUDE_API_KEY: ${{ secrets.CLAUDE_API_KEY }}
```

Store actual keys in CI/CD secrets dashboard, never in `.env` file.

## Need Help?

- **Groq**: https://console.groq.com/docs
- **Claude**: https://docs.anthropic.com/en/api
- **Project**: See CLAUDE.md for project structure

---

**Remember**: Keep your `.env` file secure and never commit it to version control!
