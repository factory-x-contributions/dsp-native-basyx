
#!/bin/bash
docker build \
  --secret id=github-token,env=GITHUB_TOKEN \
  --secret id=github-actor,env=GITHUB_ACTOR \
  -t dsp-native-basyx:latest .