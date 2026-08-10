/**
 *
 *  myapp.h
 *
 */

#pragma once

#include <drogon/HttpFilter.h>
using namespace drogon;


class myapp : public HttpFilter<myapp>
{
  public:
    myapp() {}
    void doFilter(const HttpRequestPtr &req,
                  FilterCallback &&fcb,
                  FilterChainCallback &&fccb) override;
};

