FROM hashicorp/vault:1.20

RUN apk --no-cache add bash

EXPOSE 8200
COPY ./init-vault.sh /init-vault.sh

RUN chmod 755 init-vault.sh

USER vault

ENTRYPOINT exec "./init-vault.sh"